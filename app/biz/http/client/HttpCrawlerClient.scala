package biz.http.client

import akka.actor._
import akka.contrib.throttle._
import akka.contrib.throttle.Throttler._

import biz.CrawlerExceptions._
import biz.XmlParser
import biz.config.CrawlerConfig
import crawlercommons.robots.{ BaseRobotRules, SimpleRobotRulesParser }

import play.api.libs.concurrent.Execution.Implicits._
import play.libs.Akka

import scala.concurrent.{ Promise, Future }
import scala.async.Async.{ async, await }
import scala.concurrent.duration._
import scala.util.{ Try, Success, Failure }
import scala.{ Some, None }
import spray.client.pipelining._
import spray.http._

import HttpMethods._
import spray.http.HttpResponse
import akka.contrib.throttle.Throttler.SetTarget
import akka.contrib.throttle.Throttler.Rate

/**
 * Creates a HTTP client scoped by domain, for performing GET requests
 * {{{
 *   HttpCrawlerClient("https://...")
 * }}}
 * @param uri The base URI that contains the hostname of the domain to crawl.
 */
case class HttpCrawlerClient(uri: Uri) extends HttpClientPipelines {

  val baseDomain = s"${uri.scheme}://${uri.authority.host.address}"
  val port = uri.authority.port
  val domain = if (uri.authority.port != 0) {
    s"$baseDomain:$port"
  } else {
    baseDomain
  }

  /**
   * Useful for debugging the response body of an HttpResponse
   * @param response
   */
  def printResponse(response: Future[Try[HttpResponse]]) {
    async {
      println(await(response).map(res => res.entity.asString))
    }
  }

  /**
   * Perform some GET requests in parallel
   * @param paths A traversable of relative paths to query
   * @return A future [[scala.collection.Traversable]] of [[spray.http.HttpResponse]]
   */
  def parallelGet(paths: TraversableOnce[String]): Future[TraversableOnce[Try[HttpResponse]]] = {
    Future.traverse(paths) { path =>
      get(path)
    }
  }

  /**
   * Perform a GET request on a path relative to domain.
   * {{{
   *   val client = HttpCrawlerClient("https://github.com")
   *   client.get("/")
   *   => res1: Future[Try[HttpResponse]]
   *   HttpCrawlerClient(http://www.yahoo.com).get("/")
   *   => res2: Future[Try[HttpResponse]]
   * }}}
   * @param path The path relative to domain to GET.
   * @return A future [[spray.http.HttpResponse]]
   */
  def get(path: String): Future[Try[HttpResponse]] = {
    throttledSendReceive(Get(domain + path))
  }

  /**
   * Similar to get, but allows for a custom pipeline for transforming the HttpRequest / HttpResponse
   * {{{
   *  val client = HttpCrawlerClient("https://github.com")
   *  client.get("/", sendReceive(conduit)) // gets the root page of github.com, with the basic spray.client pipeline
   * }}}
   * @param path The path relative to domain to GET.
   * @param pipe A transformation function to be done on the [[spray.http.HttpRequest]]
   * @return A future of type T
   * @tparam T The type of the result of the transformation function
   */
  def get[T](path: String, pipe: HttpRequest => Future[T]): Future[T] = {
    pipe(Get(domain + path))
  }

  /**
   * Retrieves the [[crawlercommons.robots.BaseRobotRules]] from this [[biz.http.client.HttpCrawlerClient]]'s domain.
   */
  val robotRules: Future[Try[BaseRobotRules]] = {
    fetchRules
  }

  /**
   * Future'd delay rate, throttled to 1 message per crawl-delay seconds, where crawl-delay
   * is the crawl-delay parsed from the domain's robots.txt file, or
   */
  val crawlDelayRate: Future[Rate] = {
    async {
      await(robotRules) match {
        case Success(rules) =>
          val delay = rules.getCrawlDelay
          println(s"delay!!!!:$delay")
          1 msgsPer delay.milliseconds
        case Failure(exception) =>
          // TODO: should this just throw the exception instead? at the very least, needs to log
          1 msgsPer CrawlerConfig.defaultCrawlDelay.milliseconds
      }
    }
  }

  /**
   * Retrieves the "a href" html links from the [[spray.http.HttpResponse]]
   * @param response A successful response which contains valid html to parse
   * @return a future list of anchor links from the html in [[spray.http.HttpResponse]]
   */
  def parseLinks(response: Future[Try[HttpResponse]]): Future[Try[List[String]]] = {
    async {
      for (res <- await(response)) yield {
        XmlParser.extractLinks(res.entity.asString)
      }
    }
  }

  private def fetchRules: Future[Try[BaseRobotRules]] = {
    val request = Get(domain+"/robots.txt")
    fetchRobotRules(request)
  }
}

object RobotRules {
  /**
   *
   * @param domain domain name the robots.txt contents was retrieved from
   * @param crawlerName client-agent
   * @param robotsTxtContent the contents of the robots.txt file
   * @return [[crawlercommons.robots.BaseRobotRules]]
   */
  def create(domain: String, crawlerName: String, robotsTxtContent: String): BaseRobotRules = {
    val robotParser = new SimpleRobotRulesParser()
    // Not actually immutable due to setter and getter functions
    val rules = robotParser.parseContent(domain, robotsTxtContent.getBytes, "text/plain", crawlerName)

    val delay = rules.getCrawlDelay

    // SimpleRobotRulesParser will set crawl delay to Long.MinValue or a strange negative
    // number when encountering invalid input, so overwrite it with the crawler's default delay.
    // Invalid inputs include super huge values, negative values, and values that are not numbers
    if (delay < 0 || delay == Long.MinValue) {
      rules.setCrawlDelay(CrawlerConfig.defaultCrawlDelay)
    }

    rules
  }
}

/**
 * Workaround for throttling [[spray.client]] requests
 */
trait Throttler {

  private var throttlerSet = false

  /**
   * This duration value represents how
   * @return a future'd duration of how often to poop
   */
  def crawlDelayRate: Future[Rate]

  /**
   * Dummy actor that does nothing but complete a supplied promise when a message is received
   */
  lazy val forwarder = Akka.system.actorOf(Props(new Actor {
    /**
     * Receives a [[biz.http.client.PromiseRequest]], and complete's the promise with 'true'
     */
    def receive = {
      case PromiseRequest(promise) => {
        promise success true
      }
    }
  }))

  /**
   * [[akka.contrib.throttle.TimerBasedThrottler]] that throttles messages sent to forwarder.
   * Delay rate is determined by [[biz.http.client.Throttler]].crawlDelayRate
   */
  def throttler: Future[ActorRef] = async {
    val delayRate = await(crawlDelayRate)
    val throttle = Akka.system.actorOf(Props(new TimerBasedThrottler(delayRate)))
    throttle ! SetTarget(Some(forwarder))
    throttle
  }
}

/**
 * Mini helper case class for completing a promise
 * @param p the promise to complete
 */
private[biz] case class PromiseRequest(p: Promise[Boolean])