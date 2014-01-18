package sprawler.crawler.actor

import sprawler.crawler.url.CrawlerUrl

import akka.actor.ActorRef
import akka.event.Logging

import spray.http.HttpResponse

import scala.util.{ Success, Failure, Try }
import scala.async.Async.{ async, await }
import scala.concurrent.Future
import sprawler.crawler.Streams
import play.api.libs.iteratee.Concurrent.Channel
import play.api.libs.json.JsValue

trait StreamingLinkScraper extends LinkScraper with Streams {

  override def onUrlComplete(url: CrawlerUrl, response: Try[HttpResponse]) = async {
    await(super.onUrlComplete(url, response))
    response match {
      case Success(httpResponse) =>
        streamJsonResponse(url.fromUri.toString(), url.uri.toString(), httpResponse)
      case Failure(error) =>
        streamJsonErrorFromException(error)
    }
  }

  override def onUrlNotCrawlable(url: CrawlerUrl, error: Throwable) = async {
    await(super.onUrlNotCrawlable(url, error))
    streamJsonErrorFromException(error)
  }
}

/**
 * Temporary class since Akka 2.2 doesn't allow anonymous class stackable traits in Props creation.
 *
 * @param master Reference to the master Actor.
 *               The master actor is intended to be [[sprawler.crawler.actor.LinkQueueMaster]].
 * @param originCrawlerUrl The original URL that started this crawling session. Since this URL
 *                         is the very first URL, [[sprawler.crawler.url.CrawlerUrl.fromUri]] will
 *                         be the same as [[sprawler.crawler.url.CrawlerUrl.uri]].
 * @param channel
 */
class StreamingLinkScraperWorker(
    master:           ActorRef,
    originCrawlerUrl: CrawlerUrl,
    val channel:      Channel[JsValue]
) extends LinkScraperWorker(master, originCrawlerUrl) with StreamingLinkScraper {
  val log = Logging(context.system, this)

  override def onUrlComplete(url: CrawlerUrl, response: Try[HttpResponse]) = async {
    await(super.onUrlComplete(url, response))

    response match {
      case Success(httpResponse) =>
        log.info(s"url successfully fetched: ${url.uri}")
      case Failure(error) =>
        log.error(s"url failed to fetch: ${error.getMessage}")
    }
  }

  override def onUrlNotCrawlable(url: CrawlerUrl, error: Throwable) = async {
    await(super.onUrlNotCrawlable(url, error))
    log.info(s"url not crawlable: ${url.uri} reason: ${error.getMessage}")
  }
}