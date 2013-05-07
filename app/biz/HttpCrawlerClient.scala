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
 * @param portOverride Used to override the port that the underlying spray-client actor uses.
 *                     Intended for testing.
 */
case class HttpCrawlerClient(hostName: String, portOverride: Option[Int] = None) extends Client {

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
    val request = HttpRequest(GET, "/robots.txt")
    fetchRobotRules(request)
  }
}

/**
 * Spray-client boilerplate remover.
 * Given a hostName, will create code for performing SSL (https) or non-SSL (http) requests.
 */
trait Client extends CustomPipelines {
  def hostName: String
  def portOverride: Option[Int]

  lazy val system = Akka.system

  lazy private val ioBridge = IOExtension(system).ioBridge()

  lazy private val sslOff = ConfigFactory.parseString("spray.can.client.ssl-encryption = off")
  lazy private val sslOn = ConfigFactory.parseString("spray.can.client.ssl-encryption = on")

  lazy val (truncatedHost, port, sslEnabled, sslSetting) = if (hostName.startsWith("https://")) {
    (hostName.replaceFirst("https://", ""), 443, true, sslOn
    )
  } else if (hostName.startsWith("http://")) {
    (hostName.replaceFirst("http://", ""), 80, false, sslOff)
  } else {
    portOverride match {
      case Some(portNum) => (hostName, portNum, false, sslOff)
      case None          => (hostName, 80, false, sslOff)
    }
  }

  lazy val (httpClient, conduit) = createClientAndConduit

  lazy val pipeline: HttpRequest => Future[HttpResponse] = {
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
  def conduit: ActorRef

  /**
   * Base url of the website being crawled.
   * {{{
   *   https://www.github.com/some/page (full url)
   *   => https://www.github.com (hostName)
   * }}}
   */
  def hostName: String

  /**
   * [[spray.can.client.HttpClient]] pipeline. Will only return the body of the response. Can throw a
   * [[biz.CustomExceptions.FailedHttpRequestError]] via parseBody.
   */
  lazy val bodyOnlyPipeline = {
    throttledSendReceive ~> parseBody
  }

  /**
   * [[spray.can.client.HttpClient]] pipeline. Intended to only be used for getting "robots.txt" files.
   * Does not go through throttledSendReceive, since throttledSendReceive depends on figuring out the
   * crawl delay from robots.txt (would result in a deadlock).
   */
  lazy val fetchRobotRules = {
    sendReceive(conduit) ~>
      parseBody ~>
      parseRobotRules
  }

  /**
   * Retrieve just the body of an [[spray.http.HttpResponse]].
   * @param response The future [[spray.http.HttpResponse]] to retrieve the body from.
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
          // Send a dummy object to throttler, and wait for an "ok" back from the throttler
          // to perform an action
          await(throttler) ! PromiseRequest(p)
          await(p.future)
          await(sendReceive(conduit)(request))
        }
      }
  }

  /**
   * Performs an HTTP request for the robots.txt file of siteDomain
   * @param robotsTxtBody The contents of hostName's robots.txt file
   * @return A future [[crawlercommons.robots.BaseRobotRules]]
   */
  def parseRobotRules(robotsTxtBody: Future[String]): Future[BaseRobotRules] = {
    async {
      createRobotRules(hostName, SprayCan.Client.userAgent, await(robotsTxtBody).getBytes)
    }
  }

  // TODO: figure out how to check if a request is doable or not (based on robots.txt).
  // have an undefined trait def for robot rule, and use that?
  def isRequestable(rules: Future[BaseRobotRules]): Future[Boolean] = {
    ???
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
      case PromiseRequest(promise) => {
        promise success true
      }
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
    throttler
  }

}

/**
 * Mini helper case class for completing a promise
 * @param p the promise to complete
 */
private[biz] case class PromiseRequest(p: Promise[Boolean])
