package sprawler.crawler.actor

import sprawler.concurrency.FutureImplicits._
import sprawler.config.CrawlerConfig
import sprawler.crawler.CrawlerSession._
import sprawler.crawler.url.CrawlerUrl
import sprawler.XmlParser
import sprawler.CrawlerExceptions.{ RedirectLimitReachedException, MissingRedirectUrlException }

import akka.actor.ActorRef

import spray.http.HttpResponse

import scala.async.Async.{ async, await }
import scala.util.{ Try, Success, Failure }
import scala.{ Some, None }
import scala.concurrent.Future

/**
 * Crawls, finds links, sends them to [[sprawler.crawler.actor.LinkScraperWorker.master]]
 * to queue, and waits for more links to crawl.
 *
 * @param master Reference to the master Actor.
 *               The master actor is intended to be [[sprawler.crawler.actor.LinkQueueMaster]].
 * @param originCrawlerUrl The original URL that started this crawling session. Since this URL
 *                         is the very first URL, [[sprawler.crawler.url.CrawlerUrl.fromUri]] will
 *                         be the same as [[sprawler.crawler.url.CrawlerUrl.uri]].
 * @param onUrlComplete  When a url is done being crawled by this worker, this method is called.
 *                       WARNING: this method is potentially run on a separate thread from the receive method,
 *                       ie each invocation of this method might not run in the same order that the URLs were received.
 * @param onUrlNotCrawlable This method is called with every [[sprawler.crawler.url.CrawlerUrl]] that
 *                          fails the 'isCrawlable' method
 */
class LinkScraperWorker(
  master: ActorRef,
  originCrawlerUrl: CrawlerUrl,
  onUrlComplete: (CrawlerUrl, Try[HttpResponse]) => Unit,
  onUrlNotCrawlable: (CrawlerUrl, Throwable) => Unit)
    extends Worker[CrawlerUrl](master) {

  import WorkPullingPattern._

  implicit val system = context.system

  /**
   * Attempts to crawl the provided url.
   *
   * After a url is crawled, depending on the [[spray.http.HttpResponse]].status.intValue,
   * different scenarios can occur:
   *
   * 200 - Extracts links from body, checks if the urls are crawlable,
   *       and sends the crawlable ones to the master actor.
   * 3xx - Extracts link from the 'Location' HTTP header, and sends it to
   *       the master actor for crawling, with a special 'redirectsLeft' flag.
   *
   * @param url The url to crawl. Specifically, [[sprawler.crawler.url.CrawlerUrl.uri]] is used as
   *        the target url for crawling.
   *
   * @return A Future'd HttpResponse from crawling url, which is passed to the
   *         onUrlComplete function.
   */
  def doWork(url: CrawlerUrl): Future[HttpResponse] = {

    val crawlable = url.isCrawlable

    // reject urls that are not crawlable
    crawlable match {
      case Failure(error) => onUrlNotCrawlable(url, error)
      case Success(_)     => url.session.visitedUrls.put(url.uri.toString(), true)
    }

    val futureResponse = async {
      // Complete this future with the exception created in the failed url.isCrawlable call,
      // or continue on if the url is crawlable.

      await(crawlable.asFuture)

      val client = await(retrieveClient(url.uri))

      val response = await(client.get(url.uri))

      val status = response.status.intValue

      val res: HttpResponse = if (status == 200) {
        // Find links and send them to master queue for more crawling
        XmlParser.extractLinks(response.entity.asString, url.uri.toString()) foreach { link =>
          val nextUrl = url.nextUrl(link)
          master ! Work(nextUrl)
        }
        response
      } else if (isRedirect(status)) {
        val redirectsLeft = url.redirectsLeft.getOrElse(client.crawlerConfig.maxRedirects)
        await(followRedirect(
          url,
          response,
          redirectsLeft,
          client.crawlerConfig.maxRedirects)
        )
      } else {
        response
      }
      res
    }

    futureResponse.onComplete { tryHttpResponse =>
      master ! WorkItemDone

      onUrlComplete(url, tryHttpResponse)
    }

    futureResponse
  }

  private val redirectCodes = Seq(300, 301, 302, 303, 307)

  /**
   * Only some 3xx HTTP responses have urls in the Location Header.
   * See http://en.wikipedia.org/wiki/URL_redirection#HTTP_status_codes_3xx
   */
  private def isRedirect(status: Int): Boolean = {
    if (redirectCodes.contains(status)) {
      true
    } else {
      false
    }
  }

  /**
   * Follows the specified location header in the provided redirect response
   *
   * @param url The visited url that resulted in response ([[sprawler.crawler.url.CrawlerUrl.uri]])
   * @param response The HttpResponse from crawling url ([[sprawler.crawler.url.CrawlerUrl.uri]])
   * @param redirectsLeft How many redirects we can follow before giving up. Should be decremented
   *                      each time a redirect is followed without reacing a non-3XX response status.
   *
   * @return Future'd response if successful, otherwise a Future with a Throwable
   */
  private def followRedirect(
    url: CrawlerUrl,
    response: HttpResponse,
    maxRedirects: Int,
    redirectsLeft: Int): Future[HttpResponse] = {

    require(isRedirect(response.status.intValue))

    // Find the Location header if one exists
    val locationHeader = response.headers.find { header =>
      header.lowercaseName == "location"
    }

    locationHeader match {
      case Some(link) =>
        if (redirectsLeft > 0) {
          master ! Work(url.nextUrl(link.value, Some(redirectsLeft - 1)))
          Future.successful(response)
        } else {
          Future.failed[HttpResponse](RedirectLimitReachedException(url.uri.toString(), url.uri.toString()))
        }
      case None =>
        Future.failed[HttpResponse](MissingRedirectUrlException(url.uri.toString(), "No URL found in redirect"))
    }
  }
}