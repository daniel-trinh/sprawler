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
   */
  def timeout = TestAction(parse.anyContent) { request =>
    val aPromise = Promise[Int]()
    Async {
      aPromise.future.map { i =>
        Ok(i.toString)
      }
    }
  }

  /**
   * Renders a 404.
   */
  def notFound = TestAction(parse.anyContent) { request =>
    NotFound
  }

  /**
   * Renders a 500.
   */
  def internalError = TestAction(parse.anyContent) { request =>
    InternalServerError
  }

  /**
   * Renders a 301, redirecting once to a valid route.
   */
  def redirect = TestAction(parse.anyContent) { implicit request =>
    MovedPermanently(routes.Application.echo().absoluteURL())
  }

  /**
   * Recursively redirects to self route.
   */
  def infiniteRedirect = TestAction(parse.anyContent) { implicit request =>
    MovedPermanently(routes.TestDummy.infiniteRedirect().absoluteURL())
  }

  /**
   * Renders a 404 if Play is not deployed in Test or Dev mode, otherwise executes the action
   * normally.
   *
   * @param bp How to parse the request body
   * @param f A function that returns a [[play.api.mvc.Result]]
   * @tparam A The type of content in the request
   */
  private def TestAction[A](bp: BodyParser[A])(f: Request[A] => Result): Action[A] = {
    Action(bp) { request =>
      if (!Play.isTest) {
        NotFound
      } else
        f(request)
    }
  }
}
