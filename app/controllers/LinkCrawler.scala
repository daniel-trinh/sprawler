package controllers

import play.api.Play.current
import play.api.mvc._
import play.api.libs.concurrent.Akka
import play.api.libs.json.JsValue
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.{ Promise, Future }
import scala.concurrent.duration._

import biz.{ Crawler, HttpCrawlerClient }

object LinkCrawler extends Controller {
  def deadLinks(url: String) = WebSocket.async[JsValue] { request =>
    Crawler(url).crawl
  }
}