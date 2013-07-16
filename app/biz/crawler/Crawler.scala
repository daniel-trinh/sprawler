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
import spray.client.pipelining._

import java.net.URI

import spray.http.{ Uri, HttpResponse }

// url not crawlable due to robots
// timeout
// 1xx, 2xx, 3xx, 4xx, 5xx

// Terminating conditions: Error reached, no more urls to crawl, or depth is reached.

/**
 * Entry point of Crawler.
 * @param url The initial URL to start crawling from.
 */
class Crawler(url: String) extends Streams {

  val request = spray.client.pipelining.Get(url)

  val domain = request.uri.authority.host

  val tryCrawlerUrl: Try[CrawlerUrl] = {
    if (url.startsWith("https://") || url.startsWith("http://")) {
      Success(AbsoluteUrl(url, url))
    } else {
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

  def getClient(uri: Uri, portOverride: Option[Int] = None): HttpCrawlerClient = {
    val urlKey = s"${uri.scheme}${uri.authority.host}"

    crawlerClients().get(urlKey) match {
      case Some(client) =>
        client
      case None =>
        val httpClient = HttpCrawlerClient(uri, portOverride)

        crawlerClients send { s =>
          // avoid updating the hashtable if another client has already been added asynchronously
          s.getOrElseUpdate(urlKey, httpClient)
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

  def fromUrl: Uri
  def url: Uri

  def domain: Try[String]

  def topPrivateDomain: Try[String] = {
    domain map { d =>
      // Special case localhost and 0.0.0.0, because they are valid urls
      // for local servers. Useful for testing
      if (d == "localhost" || d == "0.0.0.0") {
        d
      } else {
        Try(InternetDomainName.fromLenient(d).topPrivateDomain().name()) match {
          case Failure(error) => throw UnprocessableUrlException(fromUrl.toString(), url.toString(), error.getMessage)
          case Success(tpd)   => tpd
        }
      }
    }
  }

  /**
   * Each time a new url is found to crawl, this value is decremented by one.
   * When a url gets created and reaches 0, the crawler stops crawling. This is to prevent the crawler
   * from running indefinitely. Set to a negative number to allow for infinite crawling.
   */
  def depth: Int = CrawlerConfig.maxDepth

  def nextUrl(nextUrl: String): CrawlerUrl = {
    if (url.scheme == "https://" || url.scheme == "http://") {
      AbsoluteUrl(url, nextUrl)
    } else {
      throw UnprocessableUrlException(url.toString(), nextUrl, UnprocessableUrlException.MissingHttpPrefix)
    }
  }
}

sealed trait AbsoluteCrawlerUrl extends CrawlerUrl {
  def prefixLength: Int

  val domain: Try[String] = {
    val prependedUrl = if (url.scheme != "https://" && url.scheme != "http://") {
      s"http://$url"
    } else {
      url.toString()
    }

    val prependedUri = spray.client.pipelining.Get(prependedUrl).uri
    val host = prependedUri.authority.host.address

    if (host != "") {
      Success(host)
    } else {
      Failure(UnprocessableUrlException(fromUrl.toString(), url.toString(), "Unable to determine domain name from URL"))
    }
  }
}

case class AbsoluteUrl(fromUrl: Uri, url: Uri) extends AbsoluteCrawlerUrl {
  val prefixLength = if (url.toString().startsWith("http://")) {
    7
  } else if (url.toString().startsWith("https://")) {
    8
  }
}
//
//case class CrawlerUrl(fromUrl: String, url: Uri)

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
  val isVisited: Boolean = CrawlerAgents.visitedUrls().contains(url.toString())

  def isCrawlable(crawlerUrl: CrawlerUrl): Boolean = !isVisited && sameTPD(crawlerUrl)
}