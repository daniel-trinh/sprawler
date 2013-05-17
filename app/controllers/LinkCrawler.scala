package controllers

import play.api.Play.current
import play.api.mvc._
import play.api.libs.concurrent.Akka
import play.api.libs.json.JsValue
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.{ Promise, Future }
import scala.concurrent.duration._

import biz.{ Crawler, HttpCrawlerClient }
import akka.util.Timeout
import play.api.libs.iteratee.{ Iteratee, Concurrent, Enumerator }
import play.api.libs.EventSource

object LinkCrawler extends Controller {
  /**
   * Websocket version of dead link crawler
   * @param url
   * @return
   */
  def deadLinks(url: String) = WebSocket.async[JsValue] { request =>

    val iteratee = Iteratee.foreach[JsValue] { event =>
      play.Logger.info(s"received: $event")
    }.mapDone { event =>
      play.Logger.info(s"end: $event")
    }
    Crawler(url).crawl map { enumerator =>
      (iteratee, enumerator)
    }
  }

  /**
   * Event source version of dead link crawler
   * @param url
   */
  def deadLinksSSE(url: String) = Action {
    Async {
      Crawler(url).crawl map { enumerator =>
        Ok.stream(enumerator through EventSource())
      }
    }
  }

}