package sprawler.crawler.actor

import sprawler.concurrency.FutureImplicits._
import sprawler.crawler.CrawlerSession._
import sprawler.crawler.url.CrawlerUrl
import sprawler.HtmlParser
import sprawler.CrawlerExceptions.{ RedirectLimitReachedException, MissingRedirectUrlException }

import akka.actor.{ ActorRef, ActorSystem }

import spray.http.HttpResponse

import scala.async.Async.{ async, await }
import scala.util.{ Try, Success, Failure }
import scala.{ Some, None }
import scala.concurrent.{ Future, ExecutionContext }

import WorkPullingPattern._

trait LinkScraper {
  def master: ActorRef
  implicit def system: ActorSystem
  implicit def ec: ExecutionContext

  /**
   * When a url is done being crawled by this worker, this method is called.
   * WARNING: this method is potentially run on a separate thread from the receive method,
   * ie each invocation of this method might not run in the same order that the URLs were received.
   *
   * First parameter is the URL that was finished being crawled. Second parameter is a [[scala.util.Try]]
   * with the HTTP response of the successful crawl, or an exception that caused the crawling to fail.
   *
   * @return This future completing represents this method being done, so only complete the future after
   *         everything you want to do in this callback is complete.
   */
  def onUrlComplete(url: CrawlerUrl, response: Try[HttpResponse]): Future[Unit] = Future.successful()

  /**
   * This method is called with every [[sprawler.crawler.url.CrawlerUrl]] that
   * fails the 'isCrawlable' method.
   *
   * First parameter is the URL that couldn't be crawled.
   * Second parameter is the reason why it was not crawlable.
   *
   * @return This future completing represents this method being done, so only complete the future after
   *         everything you want to do in this callback is complete.
   */
  def onUrlNotCrawlable(url: CrawlerUrl, error: Throwable): Future[Unit] = Future.successful()

  def crawlUrl(url: CrawlerUrl) = {
    val crawlable = url.isCrawlable.asFuture

    // reject urls that are not crawlable
    val crawlableHandlers = crawlable
      .map { _ => url.session.visitedUrls.put(url.uri.toString(), true) }
      .recover { case error => onUrlNotCrawlable(url, error) }

    val futureResponse = async {
      // Complete this future with the exception created in the failed url.isCrawlable call,
      // or continue on if the url is crawlable.
      await(crawlableHandlers)

      val client = await(retrieveClient(url.uri))
      val response = await(client.get(url.uri))
      val status = response.status.intValue

      val res: HttpResponse = if (status == 200) {
        // Find links and send them to master queue for more crawling
        HtmlParser.extractLinks(response.entity.asString, url.uri.toString()) foreach { link =>
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

    crawlableHandlers andThen {
      case _ =>
        futureResponse.onComplete { tryHttpResponse =>
          onUrlComplete(url, tryHttpResponse).onComplete { _ =>
            master ! WorkItemDone
          }
        }
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

    val locationHeader = HtmlParser.extractLocationLinkFromHeaders(response.headers, url.uri.toString())

    locationHeader match {
      case Some(link) =>
        if (redirectsLeft > 0) {
          master ! Work(url.nextUrl(link, Some(redirectsLeft - 1)))
          Future.successful(response)
        } else {
          Future.failed[HttpResponse](RedirectLimitReachedException(url.uri.toString(), url.uri.toString()))
        }
      case None =>
        Future.failed[HttpResponse](MissingRedirectUrlException(url.uri.toString(), "No URL found in redirect"))
    }
  }
}