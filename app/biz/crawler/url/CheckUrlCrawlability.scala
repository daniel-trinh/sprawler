package biz.crawler.url

import biz.crawler.CrawlerAgents
import scala.util.{ Success, Failure, Try }
import biz.CrawlerExceptions.UnprocessableUrlException

trait CheckUrlCrawlability { this: CrawlerUrl =>

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
    if (this.fromUri.authority.host == this.uri.authority.host)
      true
    else
      false
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

  // May report false negatives because of Agent behavior
  val isVisited: Boolean = CrawlerAgents.visitedUrls().contains(uri.toString())

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
    } else if (!sameDomain) {
      generateUrlError(UnprocessableUrlException.NotSameOrigin)
    } else if (isVisited) {
      generateUrlError(UnprocessableUrlException.UrlAlreadyCrawled)
    } else {
      Success(Unit)
    }
  }
}