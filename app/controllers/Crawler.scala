package controllers

import play.api._
import play.api.Play.current
import libs.concurrent.Akka
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.{ Promise, Future }
import concurrent.duration._
import biz.HttpPoller

object Crawler extends Controller {
  /**
   * Finds dead links for the current domain
   */
  def deadLinks = Action {

  }
}