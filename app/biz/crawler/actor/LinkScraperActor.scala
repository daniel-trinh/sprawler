package biz.crawler.actor

import biz.config.CrawlerConfig
import biz.crawler.CrawlerAgents._
import biz.crawler.url.CrawlerUrl
import biz.http.client.RedirectFollower

import play.api.libs.iteratee.Concurrent
import play.api.libs.concurrent.Execution.Implicits._
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
class LinkScraperActor(master: ActorRef, val originCrawlerUrl: CrawlerUrl, val channel: Concurrent.Channel[JsValue])
    extends Worker[CrawlerUrl](master)
    with Streams
    with RedirectFollower {

  def doWork(url: CrawlerUrl): Future[Unit] = {
    // reject urls that are not crawlable
    if (!url.isWithinDepth) {
      // TODO: move this into a crawler exception?
      channel.push(JsObject(
        Seq("complete" -> JsString(s"Maximum crawl depth has been reached. Depth: ${CrawlerConfig.maxDepth}"))
      ))
      cleanup()
      Future(Unit)
    } else {
      url.isCrawlable match {
        case Success(_) => {
          val client = getClient(url.uri)
          async {
            val result = await(client.get(url.uri.path.toString()))
            result match {
              case Failure(err) =>
                streamJsonErrorFromException(err, eofAndEnd = false)
              case Success(response) =>
                streamJsonResponse(url.fromUri.toString(), url.uri.toString(), response)
                play.Logger.debug(s"url successfully fetched: ${url.uri.toString()}")
                val redirectResult = await(followRedirects(url, Future(Success(response))))
            }
          }
        }
        case Failure(error) => {
          streamJsonErrorFromException(error, eofAndEnd = false)
          cleanup()
          Future(Unit)
        }
      }
    }
  }
}

//class CrawlerActor(val channel: Concurrent.Channel[JsValue], val originCrawlerUrl: CrawlerUrl)
//    extends Actor with Streams with RedirectFollower {
//  import CrawlerAgents._
//
//  def receive = {
//    case url: CrawlerUrl => {
//      play.Logger.info(s"Message received: $url")
//      if (!url.isWithinDepth) {
//        // TODO: move this into a crawler exception?
//        channel.push(JsObject(
//          Seq("complete" -> JsString(s"Maximum crawl depth has been reached. Depth: ${CrawlerConfig.maxDepth}"))
//        ))
//        cleanup()
//      } else {
//        url.isCrawlable match {
//          case Success(_) => {
//            val client = getClient(url.uri)
//            async {
//              // val result = await(HttpCrawlerClient("http://localhost:9000").get("/"))
//              val result = await(client.get(url.uri.path.toString()))
//              result match {
//                case Failure(err) =>
//                  play.Logger.debug(s"url failed to fetch")
//                  streamJsonErrorFromException(err, eofAndEnd = false)
//                case Success(response) =>
//                  streamJsonResponse(url.fromUri.toString(), url.uri.toString(), response)
//                  play.Logger.debug(s"url successfully fetched: ${url.uri.toString()}")
//                  val redirectResult = await(followRedirects(url, Future(Success(response))))
//
//              }
//            }
//          }
//          case Failure(error) => {
//            streamJsonErrorFromException(error, eofAndEnd = false)
//            cleanup()
//          }
//        }
//      }
//    }
//    case m @ _ => {
//      play.Logger.info(s"Message received")
//      play.Logger.error(s"Unexpected message: $m")
//    }
//  }
//}