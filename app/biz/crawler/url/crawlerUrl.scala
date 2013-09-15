package biz.crawler.url

import biz.config.CrawlerConfig
import biz.CrawlerExceptions.UnprocessableUrlException

import scala.util.{ Failure, Success, Try }

import spray.http.Uri

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
    if (uri.scheme == "https" || uri.scheme == "http") {
      AbsoluteUrl(uri, nextUrl)
    } else {
      throw UnprocessableUrlException(uri.toString(), nextUrl, UnprocessableUrlException.MissingHttpPrefix)
    }
  }
}

sealed trait AbsoluteCrawlerUrl extends CrawlerUrl {
  val domain: Try[String] = {
    val prependedUrl = if (uri.scheme != "https" && uri.scheme != "http") {
      s"http${uri.authority.toString()}"
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

case class AbsoluteUrl(fromUri: Uri, uri: Uri) extends AbsoluteCrawlerUrl
