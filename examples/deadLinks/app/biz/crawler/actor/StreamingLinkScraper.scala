package sprawler.crawler.actor

import sprawler.crawler.url.CrawlerUrl

import akka.actor.ActorRef

import spray.http.HttpResponse

import scala.util.{Success, Failure, Try}
import scala.concurrent.Future
import sprawler.crawler.Streams
import play.api.libs.iteratee.Concurrent.Channel
import play.api.libs.json.JsValue

trait StreamingLinkScraper extends LinkScraper with Streams {

  override def onUrlComplete(url: CrawlerUrl, response: Try[HttpResponse]) {
    response match {
      case Success(httpResponse) =>
        play.Logger.info(s"url successfully fetched: ${url.uri.toString()}")
        streamJsonResponse(url.fromUri.toString(), url.uri.toString(), httpResponse)
      case Failure(error) =>
        play.Logger.error(error.getMessage)
        streamJsonErrorFromException(error)
    }
  }

  override def onUrlNotCrawlable(url: CrawlerUrl, error: Throwable) {
    play.Logger.debug("NOT CRAWLABLE:"+error.toString)
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
  master: ActorRef,
  originCrawlerUrl: CrawlerUrl,
  val channel: Channel[JsValue]
) extends LinkScraperWorker(master, originCrawlerUrl) with StreamingLinkScraper