package controllers

import play.api._
import play.api.Play.current
import libs.concurrent.Akka
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.{ Promise, Future }
import concurrent.duration._
import biz.HttpPoller

object Application extends Controller with SimplePoller {

  val aPromise = Promise[Int]()

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def helloWorld = Action {
    Ok("Hello World!")
  }

  def goodbyeWorld = Action {
    Ok("Goodbye World!")
  }

  def waiting = Action {
    Async { aPromise.future.map(i => Ok(i.toString)) }
  }

  def redeem(i: Int) = Action {
    aPromise.success(i)
    Ok("thanks")
  }

  def echo = Action { request =>
    Ok("Got request [" + request + "]")
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