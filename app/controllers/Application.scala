package controllers

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.{ Promise, Future }

object Application extends Controller {

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
