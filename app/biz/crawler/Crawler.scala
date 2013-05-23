package biz.crawler

import akka.agent._
import akka.actor._

import biz.config.CrawlerConfig
import biz.CrawlerExceptions._
import biz.CrawlerExceptions.JsonImplicits._
import biz.CrawlerExceptions.UnprocessableUrlException
import biz.http.client.HttpCrawlerClient

import crawlercommons.robots
import com.google.common.net.InternetDomainName

import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Concurrent.Channel
import play.api.Play.current

import scala.concurrent.{ Promise, Future }
import scala.collection.mutable
import scala.async.Async.{ async, await }
import scala.util.{ Try, Success, Failure }

import java.net.URI

import spray.http.HttpResponse

// url not crawlable due to robots
// timeout
// 1xx, 2xx, 3xx, 4xx, 5xx

// Terminating conditions: Error reached, no more urls to crawl, or depth is reached.

/**
 * Entry point of Crawler.
 * @param url The initial URL to start crawling from.
 */
class Crawler(url: String) extends UrlHelper with Streams {

  private val uri = new URI(url)
  lazy val domain = uri.getHost

  var channel: Concurrent.Channel[JsValue] = null
  val jsStream = Concurrent.unicast[JsValue] { chan =>
    channel = chan
    onStart(channel)
  }

  /**
   * Startup function to be called when the crawler Enumerator feed starts being consumed by an
   * Iteratee. Initiates the crawling process by sending a [[biz.crawler.UrlInfo]] message to a
   * [[biz.crawler.CrawlerActor]].
   *
   * Can only be called after channel is defined, otherwise a NPE is going to happen.
   *
   * @param channel The channel to push crawler updates into
   */
  private def onStart(channel: Channel[JsValue]) {
    play.Logger.info(s"Crawler started: $url")
    // TODO: replace this with a router for several parallel crawlers
    val crawlerActor = Akka.system.actorOf(Props(new CrawlerActor(channel)))

    calculateTPD(domain) match {
      case Failure(error) =>
        streamJsErrorFromException(error)
      case Success(tpd) =>
        lazy val crawlerUrlInfo = UrlInfo(url, url, tpd)(CrawlerAgents.visitedUrls)
        crawlerActor ! crawlerUrlInfo
    }
  }

}

trait UrlHelper {
  def calculateTPD(domain: String): Try[String] = {
    Try(InternetDomainName.fromLenient(domain).topPrivateDomain().name()) match {
      case Failure(error)   => Failure(UnprocessableUrlException(domain, error.getMessage))
      case tpd @ Success(_) => tpd
    }
  }

  def calculateDomain(url: String): String = {
    new URI(url).getHost
  }
}

/**
 * Things that need to be shared across the crawler's session
 */
object CrawlerAgents {

  /**
   * Stores all visited urls across this Crawler session's lifetime.
   */
  val visitedUrls = Agent(new mutable.HashSet[String]())(Akka.system)

  /**
   * Lookup table for robots.txt rules for a particular url
   */
  val crawlerClients = Agent(new mutable.HashMap[String, HttpCrawlerClient])(Akka.system)

  def closeAgents() {
    visitedUrls.close()
    crawlerClients.close()
  }
}

case class Links(links: List[String])

/**
 * Data class that contains information about a URL and whether or not it should be crawled.
 *
 * @param fromUrl Origin url -- the url of the page that toUrl was found on.
 * @param toUrl The url to crawl / that was crawled.
 * @param crawlerTopPrivateDomain The origin URL's "identity", where origin URL here means the URL that
 *  the crawler started crawling from. TPDs are used similar to how SHA's are
 *  used to identify code states in git. Used to check if toUrl is on the same domain as the origin URL.
 * @param depth Each time a new url is found to crawl, this value is decremented by one.
 *   When a UrlInfo gets created and reaches 0, the crawler stops crawling. This is to prevent the crawler
 *   from running indefinitely.
 * @param visitedUrls
 */
case class UrlInfo(fromUrl: String, toUrl: String, crawlerTopPrivateDomain: String, depth: Int = CrawlerConfig.maxDepth)(implicit visitedUrls: Agent[mutable.HashSet[String]]) extends UrlHelper {
  // May report false negatives because of Agent behavior
  val isVisited: Boolean = visitedUrls().contains(toUrl)
  lazy val domain: String = calculateDomain(toUrl)
  private val prefixLength = {
    if (toUrl.startsWith("https://"))
      8
    else if (toUrl.startsWith("http://"))
      7
    else throw new UnprocessableUrlException(toUrl, s"Url must start with http:// or https://.")
  }

  lazy val path: String = toUrl.substring(domain.length + prefixLength, toUrl.length)

  val topPrivateDomain: Try[String] = calculateTPD(domain)

  /**
   * Tests if the provided url's UrlHelper matches the base crawler's UrlHelper
   * {{{
   *  val url = UrlInfo("https://www.github.com/some/path", "github.com")
   *  url.sameTPD
   *  => true
   *
   *  val url = UrlInfo("https://www.github.com/some/path", "google.com")
   *  url.sameTPD
   *  => false
   * }}}
   */
  val sameTPD: Boolean = {
    if (topPrivateDomain.get == crawlerTopPrivateDomain)
      true
    else
      false
  }

  val isWithinDepth: Boolean = {
    depth <= CrawlerConfig.maxDepth
  }

  val isCrawlable: Boolean = !isVisited && sameTPD

}
