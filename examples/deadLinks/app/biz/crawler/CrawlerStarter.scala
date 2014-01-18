package sprawler.crawler

import akka.actor._

import sprawler.crawler.url.{ AbsoluteUrl, CrawlerUrl }
import sprawler.crawler.actor.WorkPullingPattern._

import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Concurrent.Channel
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Play.current

import scala.util.{ Try, Success, Failure }

import spray.http.HttpResponse
import sprawler.crawler.actor._
import scala.util.Success
import scala.util.Failure
import sprawler.crawler.actor.WorkPullingPattern.RegisterWorkerRouter
import sprawler.crawler.url.AbsoluteUrl

/**
 * Entry point of Crawler.
 * @param url The initial URL to start crawling from.
 */
class CrawlerStarter(url: String) extends Streams {
  val request = spray.client.pipelining.Get(url)

  val tryCrawlerUrl: Try[CrawlerUrl] = {

    val urlToCrawl = AbsoluteUrl(uri = url)
    val isCrawlable = urlToCrawl.isCrawlable

    isCrawlable map { _ => urlToCrawl }
  }

  var channel: Concurrent.Channel[JsValue] = null
  val jsStream = Concurrent.unicast[JsValue]({ chan =>
    channel = chan
    onStart(channel)
  }, {
    play.Logger.info("Channel closing..")
  }, { (str, input) =>
    play.Logger.error("An error is causing the channel to close..")
  })

  /**
   * Startup function to be called when the crawler Enumerator feed starts being consumed by an
   * Iteratee. Initiates the crawling process by sending a [[sprawler.crawler.url.CrawlerUrl]] message to a
   * [[sprawler.crawler.actor.LinkScraperWorker]].
   *
   * Can only be called after channel is defined, otherwise a NPE is going to happen.
   *
   * @param channel The channel to push crawler updates into
   */
  private def onStart(channel: Channel[JsValue]) {
    play.Logger.info(s"Crawler started: $url")

    tryCrawlerUrl map { crawlerUrl =>
      crawlerUrl.domain map { d =>
        val masterActor = Akka.system.actorOf(Props(classOf[StreamingLinkQueueMaster], List(crawlerUrl), channel))

        val workerRouter = Akka.system.actorOf(Props(classOf[StreamingLinkScraperWorker],
          masterActor,
          crawlerUrl,
          channel
        ))

        masterActor ! RegisterWorkerRouter(workerRouter)
      } recover {
        case error =>
          streamJsonErrorFromException(error)
          channel.end()
      }
    } recover {
      case error =>
        streamJsonErrorFromException(error)
        channel.end()
    }
  }
}