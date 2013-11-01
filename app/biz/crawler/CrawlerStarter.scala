package biz.crawler

import akka.actor._

import biz.crawler.url.{ AbsoluteUrl, CrawlerUrl }

import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Concurrent.Channel
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Play.current

import scala.util.{ Try, Success, Failure }

import spray.http.HttpResponse
import biz.crawler.actor.{ LinkQueueMaster, LinkScraperWorker }

/**
 * Entry point of Crawler.
 * @param url The initial URL to start crawling from.
 */
class CrawlerStarter(url: String) extends Streams {
  val request = spray.client.pipelining.Get(url)

  val tryCrawlerUrl: Try[CrawlerUrl] = {

    val urlToCrawl = AbsoluteUrl(uri = url)
    val isCrawlable = urlToCrawl.isCrawlable

    isCrawlable match {
      case Success(_)     => Success(urlToCrawl)
      case Failure(error) => Failure(error)
    }
  }

  var channel: Concurrent.Channel[JsValue] = null
  val jsStream = Concurrent.unicast[JsValue]({ chan =>
    channel = chan
    onStart(channel)
  }, {
    play.Logger.info("Channel closing..")
    channel.eofAndEnd()
  }, { (str, input) =>
    play.Logger.error("An error is causing the channel to close..")
    channel.eofAndEnd()
  })

  /**
   * Startup function to be called when the crawler Enumerator feed starts being consumed by an
   * Iteratee. Initiates the crawling process by sending a [[biz.crawler.url.CrawlerUrl]] message to a
   * [[biz.crawler.actor.LinkScraperWorker]].
   *
   * Can only be called after channel is defined, otherwise a NPE is going to happen.
   *
   * @param channel The channel to push crawler updates into
   */
  private def onStart(channel: Channel[JsValue]) {
    play.Logger.info(s"Crawler started: $url")

    tryCrawlerUrl match {
      case Success(crawlerUrl) => {
        crawlerUrl.domain match {
          case Success(d) =>
            // TODO: replace this with a router for several parallel crawlers
            val masterActor = Akka.system.actorOf(Props(classOf[LinkQueueMaster], List(crawlerUrl)))
            val workerRouter = Akka.system.actorOf(Props(classOf[LinkScraperWorker],
              masterActor,
              crawlerUrl,
              onUrlComplete _,
              onNotCrawlable _
            ))

          case Failure(error) =>
            streamJsonErrorFromException(error)
            cleanup()
        }
      }
      case Failure(error) => {
        streamJsonErrorFromException(error)
        cleanup()
      }
    }
  }

  private def onUrlComplete(url: CrawlerUrl, response: Try[HttpResponse]) {
    response match {
      case Success(httpResponse) =>
        play.Logger.info(s"url successfully fetched: ${url.uri.toString()}")
        streamJsonResponse(url.fromUri.toString(), url.uri.toString(), httpResponse)
      case Failure(error) =>
        play.Logger.error(error.getMessage)
        streamJsonErrorFromException(error)
    }
  }

  private def onNotCrawlable(url: CrawlerUrl, reason: Try[Unit]) {
    reason match {
      case Success(_)     => "do nothing"
      case Failure(error) => play.Logger.debug("NOT CRAWLABLE:"+error.toString())
    }
  }

}