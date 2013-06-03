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
class Crawler(url: String) extends Streams {

  val domain = (new URI(url)).getHost

  val tryCrawlerUrl: Try[CrawlerUrl] = {
    if (url.startsWith("https://")) {
      Success(AbsoluteHttpsUrl("", url))
    }
    else if (url.startsWith("http://")) {
      Success(AbsoluteHttpUrl("", url))
    }
    else {
      Failure(UnprocessableUrlException(url, url, UnprocessableUrlException.MissingHttpPrefix))
    }
  }

  var channel: Concurrent.Channel[JsValue] = null
  val jsStream = Concurrent.unicast[JsValue] { chan =>
    channel = chan
    onStart(channel)
  }

  /**
   * Startup function to be called when the crawler Enumerator feed starts being consumed by an
   * Iteratee. Initiates the crawling process by sending a [[biz.crawler.CrawlerUrl]] message to a
   * [[biz.crawler.CrawlerActor]].
   *
   * Can only be called after channel is defined, otherwise a NPE is going to happen.
   *
   * @param channel The channel to push crawler updates into
   */
  private def onStart(channel: Channel[JsValue]) {
    play.Logger.info(s"Crawler started: $url")

    tryCrawlerUrl match {
      case Success(crawlerUrl) => {
        // TODO: replace this with a router for several parallel crawlers
        val crawlerActor = Akka.system.actorOf(Props(new CrawlerActor(channel, crawlerUrl)))

        crawlerUrl.topPrivateDomain match {
          case Success(tpd) =>
            crawlerActor ! crawlerUrl
          case Failure(error) =>
            streamJsonErrorFromException(UnprocessableUrlException(url, url, error.getMessage))
        }
      }
      case Failure(error) => streamJsonErrorFromException(error)
    }

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

  def getClient(topPrivateDomain: String, domain: String): HttpCrawlerClient = {
    crawlerClients().get(topPrivateDomain) match {
      case Some(client) =>
        client
      case None =>
        val httpClient = HttpCrawlerClient(domain)

        crawlerClients send { s =>
          // avoid updating the hashtable if another client has already been added asynchronously
          s.getOrElseUpdate(topPrivateDomain, httpClient)
          s
        }
        httpClient
    }
  }

  def closeAgents() {
    visitedUrls.close()
    crawlerClients.close()
  }
}

case class Links(links: List[String])

/**
 * TODO: fix docs
 * Data class that contains information about a URL and whether or not it should be crawled.
 *
 * @param fromUrl Origin url -- the url of the page that toUrl was found on.
 * @param toUrl The url to crawl / that was crawled.
 *  the crawler started crawling from. TPDs are used similar to how SHA's are
 *  used to identify code states in git. Used to check if toUrl is on the same domain as the origin URL.
 */

sealed abstract class CrawlerUrl extends CheckUrlCrawlability {

  def fromUrl: String
  def url: String

  def domain: String
  def relativePath: String

  def topPrivateDomain: Try[String] = {
    Try(InternetDomainName.fromLenient(domain).topPrivateDomain().name()) match {
      case Failure(error)   => Failure(UnprocessableUrlException(fromUrl, url, error.getMessage))
      case tpd @ Success(_) => tpd
    }
  }

  /**
   * Each time a new url is found to crawl, this value is decremented by one.
   * When a url gets created and reaches 0, the crawler stops crawling. This is to prevent the crawler
   * from running indefinitely. Set to a negative number to allow for infinite crawling.
   */
  def depth: Int = CrawlerConfig.maxDepth

  def nextUrl(nextUrl: String): CrawlerUrl = {
    if (url.startsWith("https://")) {
      AbsoluteHttpsUrl(url, nextUrl)
    }
    else if (url.startsWith("http://")) {
      AbsoluteHttpUrl(url, nextUrl)
    }
    else {
      throw UnprocessableUrlException(url, nextUrl, UnprocessableUrlException.MissingHttpPrefix)
    }
  }

  def nextUrl(relativeNextUrl: String, domain: String): CrawlerUrl = {
    RelativeUrl(url, relativeNextUrl, domain)
  }

}

sealed trait RelativeCrawlerUrl extends CrawlerUrl {
  val relativePath = url
}

sealed trait AbsoluteCrawlerUrl extends CrawlerUrl {
  def prefixLength: Int
  val domain: String = new URI(url).getHost
  val relativePath = {
    val pathAttempt = url.substring(domain.length + prefixLength, url.length)
    if (pathAttempt == "")
      "/"
    else
      pathAttempt
  }
}

case object EmptyUrl extends CrawlerUrl {
  def url: Nothing = throw new RuntimeException("Invalid method access on EmptyUrl.")
  def fromUrl: Nothing = throw new RuntimeException("Invalid method access on EmptyUrl.")
  def domain: Nothing = throw new RuntimeException("Invalid method access on EmptyUrl.")
  def relativePath: Nothing = throw new RuntimeException("Invalid method access on EmptyUrl.")
}

case class AbsoluteHttpsUrl(fromUrl: String, url: String) extends AbsoluteCrawlerUrl {
  val prefixLength = 8
}

case class AbsoluteHttpUrl(fromUrl: String, url: String) extends AbsoluteCrawlerUrl {
  val prefixLength = 7
}

case class RelativeUrl(fromUrl: String, url: String, domain: String) extends RelativeCrawlerUrl

trait CheckUrlCrawlability { this: CrawlerUrl =>

  /**
   * Tests if the provided url's UrlHelper matches the base crawler's UrlHelper.
   * TPDs are used similar to how SHA's are used to identify code states in git.
   * Used to check if toUrl is on the same domain as the origin URL.
   * TODO: fix this doc example
   * {{{
   *  val url = UrlInfo("https://www.github.com/some/path", "github.com")
   *  url.sameTPD
   *  => true
   *
   *  val url = UrlInfo("https://www.github.com/some/path", "google.com")
   *  url.sameTPD
   *  => false
   * }}}
   * @param crawlerUrl
   */
  def sameTPD(crawlerUrl: CrawlerUrl): Boolean = {
    if (this.topPrivateDomain.get == crawlerUrl.topPrivateDomain)
      true
    else
      false
  }

  val isWithinDepth: Boolean = {
    depth <= CrawlerConfig.maxDepth
  }

  // May report false negatives because of Agent behavior
  val isVisited: Boolean = CrawlerAgents.visitedUrls().contains(url)

  def isCrawlable(crawlerUrl: CrawlerUrl): Boolean = !isVisited && sameTPD(crawlerUrl)
}