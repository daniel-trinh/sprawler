package biz.crawler.actor

import biz.concurrency.FutureImplicits._
import biz.config.CrawlerConfig
import biz.crawler.CrawlerAgents._
import biz.crawler.url.CrawlerUrl
import biz.http.client.RedirectFollower

import play.api.libs.iteratee.Concurrent
import play.api.libs.json.{ JsString, JsObject, Json, JsValue }

import akka.actor.{ ActorRef, Actor }

import spray.http.{ HttpHeader, HttpResponse }

import scala.async.Async.{ async, await }
import scala.util.{ Try, Success, Failure }
import scala.{ Some, None }
import scala.concurrent.Future
import biz.crawler.Streams

/**
 * Crawls, finds links, sends them to Master to queue, and waits for more links to crawl.
 * @param master Reference to the master Actor
 * @param originCrawlerUrl The original URL that started this crawling session
 * @param channel Channel for pushing results into
 */
class LinkScraperWorker(master: ActorRef, val originCrawlerUrl: CrawlerUrl, val channel: Concurrent.Channel[JsValue])
    extends Worker[CrawlerUrl](master)
    with Streams
    with RedirectFollower {

  import WorkPullingPattern._

  def doWork(url: CrawlerUrl): Future[Unit] = {
    // reject urls that are not crawlable
    val futureWork = async {
      await(url.isCrawlable.asFuture)
      val client = getClient(url.uri)

      val result = client.get(url.uri.path.toString())
      val response = await(client.get(url.uri.path.toString()))

      streamJsonResponse(url.fromUri.toString(), url.uri.toString(), response)

      play.Logger.debug(s"url successfully fetched: ${url.uri.toString()}")

      val redirectResult = await(followRedirects(url, Future(response)))
    }

    futureWork.onComplete { _ =>
      master ! WorkItemDone
    }
    futureWork
  }
}