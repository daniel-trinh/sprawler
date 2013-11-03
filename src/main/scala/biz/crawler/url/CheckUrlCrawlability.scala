package biz.crawler.url

import biz.crawler.CrawlerSession
import biz.CrawlerExceptions.{ UnprocessableUrlException, RedirectLimitReachedException }

import scala.util.{ Success, Failure, Try }
import spray.http.Uri.Empty
import java.util.concurrent.ConcurrentHashMap

trait CheckUrlCrawlability { this: CrawlerUrl =>

  /**
   * Required for figuring out which URLs have been crawled during this crawling
   * session.
   * See [[biz.crawler.CrawlerSession.visitedUrls]].
   * @return
   */
  def session: CrawlerSession

  /**
   * Tests if the provided url's UrlHelper matches the base crawler's UrlHelper.
   * TPDs are used similar to how SHA's are used to identify code states in git.
   * Used to check if toUrl is on the same domain as the origin URL.
   * {{{
   *  val url = CrawlerUrl("https://www.github.com/some/path", "github.com")
   *  url.sameDomain
   *  => true
   *
   *  val url = CrawlerUrl("https://www.github.com/some/path", "google.com")
   *  url.sameDomain
   *  => false
   * }}}
   */
  def sameDomain: Boolean = {
    if (this.fromUri != Empty) {
      if (this.fromUri.authority.host == this.uri.authority.host)
        true
      else
        false
    } else {
      true
    }
  }

  val hasRedirectsLeft: Boolean = {
    redirectsLeft match {
      case Some(num) => num > 0
      case None      => true
    }
  }

  val isWithinDepth: Boolean = {
    depth >= 0
  }

  val hasValidScheme: Boolean = {
    val scheme = this.uri.scheme
    if (scheme == "http" || scheme == "https")
      true
    else
      false
  }

  val hasValidDomain: Boolean = {
    val domain = this.uri.authority.host
    if (domain.toString == "")
      false
    else
      true
  }

  /**
   * May contain false negatives, since [[java.util.concurrent.ConcurrentHashMap]]
   *
   */
  def isVisited: Boolean = {
    session.visitedUrls.containsKey(uri.toString())
  }

  /**
   * This method determines whether or not this url can be crawled.
   *
   * A url is crawlable if:
   * - Domain/Hostnames match
   * - Scheme is http or https
   * - The url hasn't already been crawled
   *
   * @return Success(Unit) if crawlable, otherwise a Failure[[biz.CrawlerExceptions.UnprocessableUrlException]]
   *         is returned, with the reason why the URL couldn't be crawled.
   */
  def isCrawlable: Try[Unit] = {

    def generateUrlError(message: String): Try[Unit] = {
      Failure(
        UnprocessableUrlException(
          fromUrl = this.fromUri.toString(),
          toUrl = this.uri.toString(),
          message = message
        )
      )
    }

    if (!hasValidScheme) {
      generateUrlError(UnprocessableUrlException.MissingHttpPrefix)
    } else if (!hasValidDomain) {
      generateUrlError(UnprocessableUrlException.InvalidDomain)
    } else if (!isWithinDepth) {
      generateUrlError(UnprocessableUrlException.MaxDepthReached)
    } else if (!hasRedirectsLeft) {
      Failure(RedirectLimitReachedException(this.fromUri.toString(), this.uri.toString()))
    } else if (!sameDomain) {
      generateUrlError(UnprocessableUrlException.NotSameOrigin)
    } else if (isVisited) {
      generateUrlError(UnprocessableUrlException.UrlAlreadyCrawled)
    } else {
      Success(Unit)
    }
  }
}