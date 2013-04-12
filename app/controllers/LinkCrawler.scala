package controllers

import play.api.Play.current
import play.api.mvc._
import play.api.libs.concurrent.Akka
import play.api.libs.json.JsValue
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.{ Promise, Future }
import scala.concurrent.duration._

import biz.{ Crawler, HttpPoller }

object LinkCrawler extends Controller with SimplePoller {
  def crawl(domain: String) = WebSocket.async[JsValue] { request =>
    Crawler.crawl
  }
}

trait SimplePoller extends HttpPoller {

  // Needs to be lazy to avoid null initialization order problems
  lazy val system = Akka.system
  lazy val hostName = "0.0.0.0"
  lazy val port = 9000

  // Schedule a periodic task to occur every 5 seconds, starting as soon as this schedule is registered
  system.scheduler.schedule(initialDelay = 0 seconds, interval = 5 seconds) {
    val paths = Seq("helloWorld", "goodbyeWorld")
    pollService(paths)
  }
}