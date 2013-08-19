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
import play.api.Logger

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

        crawlerUrl.domain match {
          case Success(d) =>
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

  /**
   *
   * @param uri This is the URI of the URL that you want to crawl next, and not the URI of an already
   *            crawled URL.
   * @param portOverride This is used for unit tests.
   * @return
   */
  def getClient(uri: Uri, portOverride: Option[Int] = None): HttpCrawlerClient = {
    val urlKey = s"${uri.scheme}${uri.authority.host}"

    crawlerClients().get(urlKey) match {
      case Some(client) =>
        client
      case None =>
        val httpClient = HttpCrawlerClient(uri)
        Logger.debug(s"httpClientUri:$uri")
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
 * Data class that contains information about a URL and whether or not it should be crawled.
 */
sealed abstract class CrawlerUrl extends CheckUrlCrawlability {

  /**
   * Used to tell what the previous URI that was crawled was.
   * @return Uri that uri originated from.
   */
  def fromUri: Uri

  /**
   * Used to tell what the next URI to crawl is, or was just crawled.
   * @return Current Uri, to crawl, or was just crawled.
   */
  def uri: Uri

  /**
   * URI scheme plus URI host name. Used primarily for throttling purposes -- if a URL
   * has the same domain as a URL that has already been crawled, we don't want to recrawl it
   * before the crawl delay has been reached.
   */
  def domain: Try[String]

  /**
   * Each time a new url is found to crawl, this value is decremented by one.
   * When a url gets created and reaches 0, the crawler stops crawling. This is to prevent the crawler
   * from running indefinitely. Set to a negative number to allow for infinite crawling.
   */
  def depth: Int = CrawlerConfig.maxDepth

  /**
   * TODO: remove this? where is it used?
   * @param nextUrl
   * @return
   */
  def nextUrl(nextUrl: String): CrawlerUrl = {
    if (uri.scheme == "https://" || uri.scheme == "http://") {
      AbsoluteUrl(uri, nextUrl)
    } else {
      throw UnprocessableUrlException(uri.toString(), nextUrl, UnprocessableUrlException.MissingHttpPrefix)
    }
  }
}

sealed trait AbsoluteCrawlerUrl extends CrawlerUrl {
  def prefixLength: Int

  val domain: Try[String] = {
    val prependedUrl = if (uri.scheme != "https://" && uri.scheme != "http://") {
      s"http://${uri.authority.toString()}"
    } else {
      s"${uri.scheme}${uri.toString()}"
    }

    val prependedUri = spray.client.pipelining.Get(prependedUrl).uri
    val host = prependedUri.authority.host.address
    if (host != "") {
      Success(host)
    } else {
      Failure(UnprocessableUrlException(fromUri.toString(), uri.toString(), "Unable to determine domain name from URL"))
    }
  }
}

case class AbsoluteUrl(fromUri: Uri, uri: Uri) extends AbsoluteCrawlerUrl {
  val prefixLength = if (uri.scheme == "http://") {
    7
  } else if (uri.scheme == "https://") {
    8
  } else {
    throw UnprocessableUrlException(fromUri.toString(), uri.toString(), "Url must start with http:// or https://")
  }
}

//case class CrawlerUrl(fromUri: String, url: Uri)

trait CheckUrlCrawlability { this: CrawlerUrl =>

  /**
   * Tests if the provided url's UrlHelper matches the base crawler's UrlHelper.
   * TPDs are used similar to how SHA's are used to identify code states in git.
   * Used to check if toUrl is on the same domain as the origin URL.
   * TODO: fix this doc example
   * {{{
   *  val url = CrawlerUrl("https://www.github.com/some/path", "github.com")
   *  url.sameTPD
   *  => true
   *
   *  val url = CrawlerUrl("https://www.github.com/some/path", "google.com")
   *  url.sameTPD
   *  => false
   * }}}
   * @param crawlerUrl
   */
  def sameTPD(crawlerUrl: CrawlerUrl): Boolean = {
    if (this.domain.get == crawlerUrl.domain.get)
      true
    else
      false
  }

  val isWithinDepth: Boolean = {
    depth <= CrawlerConfig.maxDepth
  }

  // May report false negatives because of Agent behavior
  val isVisited: Boolean = CrawlerAgents.visitedUrls().contains(uri.toString())

  def isCrawlable(crawlerUrl: CrawlerUrl): Boolean = !isVisited && sameTPD(crawlerUrl)
}