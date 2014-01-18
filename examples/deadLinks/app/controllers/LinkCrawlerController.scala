package controllers

import play.api.mvc._
import play.api.libs.json.JsValue
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.{ Iteratee, Enumerator }
import play.api.libs.EventSource

import scala.concurrent.Future

import sprawler.crawler.CrawlerStarter

object LinkCrawlerController extends Controller {
  /**
   * Websocket version of dead link crawler
   * @param url
   * @return
   */
  def deadLinks(url: String) = WebSocket.async[JsValue] { request =>

    // TODO: fix data race problem with actor starting crawling before enumerator is received
    val iteratee = Iteratee.foreach[JsValue] { event =>
      play.Logger.info(s"received: $event")
    }.map { event =>
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
    Ok.chunked(crawler.jsStream through EventSource()).as("text/event-stream")
  }
}
