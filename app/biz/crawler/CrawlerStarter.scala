package biz.crawler

import akka.agent._
import akka.actor._

import biz.crawler.url.{ AbsoluteUrl, CrawlerUrl }
import biz.crawler.actor.WorkPullingPattern._

import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Concurrent.Channel
import play.api.Play.current

import scala.concurrent.{ Promise, Future }
import scala.collection.mutable
import scala.async.Async.{ async, await }
import scala.util.{ Try, Success, Failure }

import spray.http.{ Uri, HttpResponse }
import biz.crawler.actor.{ Master, LinkScraperWorker }

// url not crawlable due to robots
// timeout
// 1xx, 2xx, 3xx, 4xx, 5xx

// Terminating conditions: Error reached, no more urls to crawl, or depth is reached.

/**
 * Entry point of Crawler.
 * @param url The initial URL to start crawling from.
 */
class CrawlerStarter(url: String) extends Streams {

  val request = spray.client.pipelining.Get(url)

  val domain = request.uri.authority.host

  val tryCrawlerUrl: Try[CrawlerUrl] = {

    val urlToCrawl = AbsoluteUrl(url, url)
    val isCrawlable = urlToCrawl.isCrawlable

    isCrawlable match {
      case Success(_)     => Success(AbsoluteUrl(url, url))
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

        // TODO: replace this with a router for several parallel crawlers
        val masterActor = Akka.system.actorOf(Props(new Master[CrawlerUrl]()))
        masterActor ! Work(crawlerUrl)
        val crawlerActor = Akka.system.actorOf(Props(new LinkScraperWorker(masterActor, crawlerUrl, channel)))

        crawlerUrl.domain match {
          case Success(d) =>
            // Start the crawling process by sending the crawler actor a url to crawl
            crawlerActor ! crawlerUrl
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
}

case class Links(links: List[String])