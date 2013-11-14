package controllers

import play.api.Play.current
import play.api.mvc._
import play.api.libs.concurrent.Akka
import play.api.libs.json.JsValue
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.{ Promise, Future }
import scala.concurrent.duration._

import akka.util.Timeout
import play.api.libs.iteratee.{ Iteratee, Concurrent, Enumerator }
import play.api.libs.EventSource
import sprawler.crawler.CrawlerStarter

object LinkCrawler extends Controller {
  /**
   * Websocket version of dead link crawler
   * @param url
   * @return
   */
  def deadLinks(url: String) = WebSocket.async[JsValue] { request =>

    // TODO: fix data race problem with actor starting crawling before enumerator is received
    val iteratee = Iteratee.foreach[JsValue] { event =>
      play.Logger.info(s"received: $event")
    }.mapDone { event =>
      play.Logger.info(s"end: $event")
    }
    val crawler = new CrawlerStarter(url)
    Future.successful(iteratee, crawler.jsStream)
  }

  /**
   * Event source version of dead link crawler
   * @param url
   */
  def deadLinksSSE(url: String) = Action {
    // TODO: fix data race problem with actor starting crawling before enumerator is received
    val crawler = new CrawlerStarter(url)
    Ok.stream(crawler.jsStream through EventSource() andThen Enumerator.eof).as("text/event-stream")
  }
}