package biz.crawler.url

import biz.config.CrawlerConfig
import biz.CrawlerExceptions.UnprocessableUrlException

import scala.util.{ Failure, Success, Try }

import spray.http.Uri
import spray.http.Uri.Empty

/**
 * Data class that contains information about a URL and whether or not it should be crawled.
 *
 * [[biz.crawler.url.CrawlerUrl.fromUri]] and [[biz.crawler.url.CrawlerUrl.uri]] are used to
 * tell the user two things:
 *
 * 1) Which URL is the one about to be / has been crawled?
 * 2) Which URL did this URL originate from (what web page URL was it found on)?
 *
 * 'fromUri' and 'uri' were not designed as CrawlerUrls themselves, to prevent a long chain
 * of references (ie a doubly linked list), so that CrawlerUrls that are no longer
 * being used can be garbage collected.
 *
 * This is important to prevent memory leaks in the case of long crawling sessions.
 * If it was implemented as a doubly linked list of CrawlerUrls, the CrawlerUrl
 * chain would only be garbe collected once the crawling session is considered "over",
 * which can be an arbitrary and unknown amount of time.
 */
sealed abstract class CrawlerUrl {

  def isCrawlable: Try[Unit]

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
  def depth: Int

  /**
   * This is used to count the number of times we've been following a particular redirect
   * @return
   */
  def redirectsLeft: Option[Int]

  /**
   * @param nextUrl If this doesn't have a valid URI scheme, the previous URI's scheme and authority will be used
   *                as a prefix to this nextUrl for creating the next uri.
   * @return
   */
  def nextUrl(nextUrl: String, redirects: Option[Int] = None): CrawlerUrl = {
    val nextUrlTrimmed = nextUrl.takeWhile { char =>
      char != '#'
    }

    AbsoluteUrl(
      fromUri = uri,
      uri = nextUrlTrimmed,
      depth = depth - 1,
      redirectsLeft = redirects
    )
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

case class AbsoluteUrl(
  fromUri: Uri = Empty,
  uri: Uri,
  depth: Int = CrawlerConfig.maxDepth,
  redirectsLeft: Option[Int] = None) extends AbsoluteCrawlerUrl with CheckUrlCrawlability