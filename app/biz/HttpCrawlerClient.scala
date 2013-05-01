package biz

import akka.actor._
import akka.contrib.throttle._
import akka.contrib.throttle.Throttler._

import biz.CustomExceptions._
import biz.config.SprayCan
import crawlercommons.robots.{ BaseRobotRules, SimpleRobotRulesParser }

import play.api.libs.concurrent.Execution.Implicits._
import play.libs.Akka

import com.typesafe.config.{ Config, ConfigFactory }

import scala.concurrent.{ Promise, Future }
import scala.async.Async.{ async, await }
import scala.concurrent.duration._

import spray.can.client.HttpClient
import spray.client.HttpConduit
import spray.io._
import spray.http._
import HttpMethods._
import HttpConduit._

/**
 * Creates a HTTP client scoped by hostName, for performing GET requests
 * {{{
 *   HttpCrawlerClient("https://...")
 * }}}
 * @param hostName The base url, including protocol prefix (http:// or https://)
 */
case class HttpCrawlerClient(hostName: String) extends Client with CustomPipelines {

  def printResponse(response: Future[HttpResponse]) {
    async {
      println(await(response).entity.asString)
    }
  }

  /**
   * Perform some GET requests in parallel
   * @param paths A traversable of relative paths to query
   * @return A future [[scala.collection.Traversable]] of [[spray.http.HttpResponse]]
   */
  def parallelGet(paths: TraversableOnce[String]): Future[TraversableOnce[HttpResponse]] = {
    Future.traverse(paths) { path =>
      get(path)
    }
  }

  /**
   * Perform a GET request on a path relative to hostName.
   * {{{
   *   val client = HttpCrawlerClient("https://github.com")
   *   client.get("/")
   *   => res1: Future[HttpResponse]
   *   HttpCrawlerClient(http://www.yahoo.com).get("/")
   *   => res2: Future[HttpResponse]
   * }}}
   * @param path The path relative to hostName to GET.
   * @return A future [[spray.http.HttpResponse]]
   */
  def get(path: String): Future[HttpResponse] = {
    pipeline(HttpRequest(GET, path))
  }

  /**
   * Similar to get, but allows for a custom pipeline for transforming the HttpRequest / HttpResponse
   * {{{
   *  val client = HttpCrawlerClient("https://github.com")
   *  client.get("/", sendReceive(conduit)) // gets the root page of github.com, with the basic spray.client pipeline
   * }}}
   * @param path The path relative to hostName to GET.
   * @param pipe A transformation function to be done on the [[spray.http.HttpRequest]]
   * @return A future of type T
   * @tparam T The type of the result of the transformation function
   */
  def get[T](path: String, pipe: HttpRequest => Future[T]): Future[T] = {
    pipe(HttpRequest(GET, path))
  }

  /**
   * Future'd delay rate, throttled to 1 message per crawl-delay seconds, where crawl-delay
   * is the crawl-delay parsed from the domain's robots.txt file, or
   */
  lazy val crawlDelayRate: Future[Rate] = {
    async {
      1 msgsPer (await(robotRules).getCrawlDelay.seconds)
    }
  }

  /**
   * Retrieves the [[crawlercommons.robots.BaseRobotRules]] from this [[biz.HttpCrawlerClient]]'s domain.
   */
  lazy val robotRules: Future[BaseRobotRules] = {
    fetchRules
  }

  /**
   * Retrieves the "a href" html links from the [[spray.http.HttpResponse]]
   * @param response A successful response which contains valid html to parse
   * @return a future list of anchor links from the html in [[spray.http.HttpResponse]]
   */
  def parseLinks(response: Future[HttpResponse]): Future[List[String]] = {
    response map { result =>
      XmlParser.extractLinks(result.entity.asString)
    }
  }

  private def fetchRules: Future[BaseRobotRules] = {
    fetchRobotRules(HttpRequest(GET, "/robots.txt"))
  }
}

/**
 * Spray-client boilerplate remover.
 * Given a hostName, will create code for performing SSL (https) or non-SSL (http) requests.
 */
trait Client {
  val hostName: String
  val system = Akka.system

  private val ioBridge = IOExtension(system).ioBridge()

  lazy private val sslOff = ConfigFactory.parseString("spray.can.client.ssl-encryption = off")
  lazy private val sslOn = ConfigFactory.parseString("spray.can.client.ssl-encryption = on")

  private val (truncatedHost, port, sslEnabled, sslSetting) = if (hostName.startsWith("https://")) {
    (hostName.replaceFirst("https://", ""), 443, true, sslOn
    )
  } else if (hostName.startsWith("http://")) {
    (hostName.replaceFirst("http://", ""), 80, false, sslOff)
  } else {
    (hostName, 80, false, sslOff)
  }

  val (httpClient, conduit) = createClientAndConduit

  val pipeline: HttpRequest => Future[HttpResponse] = {
    sendReceive(conduit)
  }

  private def createClientAndConduit: (ActorRef, ActorRef) = {
    val httpClient = system.actorOf(Props(new HttpClient(ioBridge, sslSetting)))
    val conduit = system.actorOf(Props(new HttpConduit(httpClient, truncatedHost, port, sslEnabled)))
    (httpClient, conduit)
  }
}

trait CustomPipelines extends Throttler {

  /**
   * [[spray.can.client.HttpClient]] conduit actor. It is left abstract to allow for the user
   * to specify their own conduit as needed. This is a boilerplate requirement in order to perform HTTP requests
   * using spray.client.
   */
  val conduit: ActorRef

  /**
   * [[spray.can.client.HttpClient]] pipeline. Will only return the body of the response. Can throw a
   * [[biz.CustomExceptions.FailedHttpRequestError]] via parseBody.
   */
  val bodyOnlyPipeline = {
    throttledSendReceive ~> parseBody
  }

  /**
   * [[spray.can.client.HttpClient]] pipeline. Intended to only be used for getting "robots.txt" files.
   */
  val fetchRobotRules = {
    bodyOnlyPipeline ~> parseRobotRules
  }

  /**
   * Retrieve just the body of an [[spray.http.HttpResponse]].
   * @param response
   * @throws FailedHttpRequestError This is thrown if the HttpResponse is not a 1xx, 2xx, or 3xx status response.
   * @return future'd parsed response body
   */
  def parseBody(response: Future[HttpResponse]): Future[String] = {
    response map { res =>
      if (res.status.isSuccess)
        res.entity.asString
      else
        throw new FailedHttpRequestError("")
    }
  }

  /**
   * Intended to be a throttled drop in replacement for [[spray.client.HttpConduit]].sendReceive
   * @return A function that receives an [[spray.http.HttpRequest]] and returns a future [[spray.http.HttpResponse]].
   */
  def throttledSendReceive: HttpRequest => Future[HttpResponse] = {
    request =>
      {
        val p = Promise[Boolean]()
        async {
          await(throttler) ! PromiseRequest(p)
        }

        async {
          await(p.future) // wait for the throttler to notify us that it's OK to perform another request
          await(sendReceive(conduit)(request))
        }
      }
  }

  /**
   * Performs an HTTP request for the robots.txt file of siteDomain
   * {{{
   * }}}
   * @param siteDomain A working base url to fetch robots.txt from
   * @return A future [[crawlercommons.robots.BaseRobotRules]]
   */
  def parseRobotRules(siteDomain: Future[String]): Future[BaseRobotRules] = {
    for {
      domain <- siteDomain
      crawler = HttpCrawlerClient(domain)
      response = crawler.get("robots.txt", crawler.bodyOnlyPipeline)
      res <- response
    } yield {
      createRobotRules(domain, SprayCan.userAgent, res.getBytes)
    }
  }

  /**
   *
   * @param domain domain name the robots.txt contents was retrieved from
   * @param crawlerName client-agent
   * @param content the contents of the robots.txt file
   * @return [[crawlercommons.robots.BaseRobotRules]]
   */
  private def createRobotRules(domain: String, crawlerName: String, content: Array[Byte]): BaseRobotRules = {
    val robotParser = new SimpleRobotRulesParser()
    robotParser.parseContent(domain, content, "text/plain", crawlerName)
  }
}

/**
 * Workaround for throttling [[spray.can.client.HttpClient]] requests
 */
trait Throttler {

  /**
   * This duration value represents how
   * @return a future'd duration of how often to poop
   */
  def crawlDelayRate: Future[Rate]

  /**
   * Dummy actor that does nothing but complete a supplied promise when a message is received
   */
  val forwarder = Akka.system.actorOf(Props(new Actor {
    /**
     * Receives a [[biz.PromiseRequest]], and complete's the promise with 'true'
     */
    def receive = {
      case PromiseRequest(promise) => promise success true
    }
  }))

  /**
   * [[akka.contrib.throttle.TimerBasedThrottler]] that throttles messages sent to forwarder.
   * Delay rate is determined by [[biz.Throttler]].crawlDelayRate
   */
  val throttler: Future[ActorRef] = async {
    val delayRate = await(crawlDelayRate)
    val throttler = Akka.system.actorOf(Props(new TimerBasedThrottler(delayRate)))
    throttler ! SetTarget(Some(forwarder))
    // Set the target
    throttler
  }

}

/**
 * Mini helper case class for completing a promise
 * @param p the promise to complete
 */
private[biz] case class PromiseRequest(p: Promise[Boolean])
