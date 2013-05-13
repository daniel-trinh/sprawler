package controllers

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.{ Promise, Future }
import play.api.Play
import play.api.Play.current

/**
 * A bunch of actions for testing the crawler code. Not intended
 * to be exposed as a public HTTP API
 */
object TestDummy extends Controller {

  val aPromise = Promise[Int]()

  /**
   * This route will never finish.
   * @return
   */
  def timeout = TestAction(parse.anyContent) { request =>
    val aPromise = Promise[Int]()
    Async {
      aPromise.future.map { i =>
        Ok(i.toString)
      }
    }
  }

  def notFound = TestAction(parse.anyContent) { request =>
    NotFound
  }

  def internalError = TestAction(parse.anyContent) { request =>
    InternalServerError
  }

  def redirect = TestAction(parse.anyContent) { implicit request =>

    MovedPermanently(routes.Application.echo().absoluteURL())
  }

  private def TestAction[A](bp: BodyParser[A])(f: Request[A] => Result): Action[A] = {
    Action(bp) { request =>
      if (!Play.isTest) {
        NotFound
      } else
        f(request)
    }
  }
}
