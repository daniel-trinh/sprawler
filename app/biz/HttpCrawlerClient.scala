package biz

import akka.actor._
import spray.can.client.HttpClient
import spray.client.HttpConduit
import spray.io._
import spray.http._
import crawlercommons.robots.{ BaseRobotRules, SimpleRobotRulesParser }
import scala.concurrent.{ Promise, Future }
import HttpMethods._
import HttpConduit._
import play.api.libs.concurrent.Execution.Implicits._
import play.libs.Akka
import com.typesafe.config.{ Config, ConfigFactory }
import scala.async.Async.{ async, await }
import biz.CustomExceptions._
import biz.config.SprayCan
import scala.concurrent.duration._
import akka.contrib.throttle._
import akka.contrib.throttle.Throttler._

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
   *   val client = HttpCrawlerClient(https://github.com)
   *   client.get('/')
   *   => res1: Future[HttpResponse]
   *   HttpCrawlerClient(http://www.yahoo.com).get('/')
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
   *   TODO: add an example here
   * }}}
   * @param path The path relative to hostName to GET.
   * @param pipe A transformation function
   * @return A future of type T
   * @tparam T The type of the result of the transformation function
   */
  def get[T](path: String, pipe: HttpRequest => Future[T]): Future[T] = {
    pipe(HttpRequest(GET, path))
  }

  lazy val crawlDelay: Future[FiniteDuration] = {
    async {
      await(robotRules).getCrawlDelay.seconds
    }
  }

  lazy val robotRules: Future[BaseRobotRules] = {
    fetchRules
  }

  def fetchRules: Future[BaseRobotRules] = {
    fetchRobotRules(HttpRequest(GET, "/robots.txt"))
  }

  def parseHtml(response: Future[HttpResponse]): Future[List[String]] = {
    response map { result =>
      XmlParser.extractLinks(result.entity.asString)
    }
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

trait CustomPipelines {

  val conduit: ActorRef
  def crawlDelay: Future[FiniteDuration]

  val bodyOnlyPipeline = {
    sendReceive(conduit) ~> parseBody
  }
  val fetchRobotRules = {
    bodyOnlyPipeline ~> parseRobotRules
  }

  def parseBody(response: Future[HttpResponse]): Future[String] = {
    response map { res =>
      if (res.status.isSuccess)
        res.entity.asString
      else
        throw new FailedHttpRequestError("")
    }
  }

  case class PromiseRequest(p: Promise[HttpRequest], request: HttpRequest)

  val throttler = Akka.system.actorOf(Props(new TimerBasedThrottler(
    1 msgsPer (1.second))))
  // Set the target
  throttler ! SetTarget(Some(forwarder))

  val forwarder = Akka.system.actorOf(Props(new Actor {
    def receive = {
      case PromiseRequest(promise, request) => promise success request
    }
  }))

  // TODO: Figure out how to get this working with pipelining
  // maybe creating a futureSendReceive (Future[HttpRequest] => Future[HttpResponse] and chaining it with this?
  def throttledSendReceive(request: HttpRequest): Future[HttpRequest] = {
    val p = Promise[HttpRequest]()

    throttler ! PromiseRequest(p, request)

    p.future
  }

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

  private def createRobotRules(domain: String, crawlerName: String, content: Array[Byte]): BaseRobotRules = {
    val robotParser = new SimpleRobotRulesParser()
    robotParser.parseContent(domain, content, "text/plain", crawlerName)
  }
}