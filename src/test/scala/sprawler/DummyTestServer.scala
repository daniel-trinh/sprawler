package sprawler

import spray.routing.SimpleRoutingApp
import spray.http.{ Uri, StatusCodes }

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.testkit.TestKit
import akka.io.IO
import akka.util.Timeout

import scala.util.{ Success, Random }
import scala.concurrent.{ Await, ExecutionContext, Promise, Future }
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import spray.can.Http

/**
 * Shared Spray HTTP Test server with some basic routes.
 */
object DummyTestServer extends SimpleRoutingApp {

  private var serverBoundMsg: Option[Http.Bound] = None

  /**
   * Starts a test server.
   *
   * Be careful when using this, it is currently coded in a way
   * that can result in race conditions (server started, another thread shuts it down,
   * previous thread assumes it was up).
   *
   * @param port Port override, defaults to [[sprawler.SpecHelper.port]].
   * @param system ActorSystem to create this [[spray.routing.SimpleRoutingApp]] in.
   * @param ctx Used in order to start the server (it uses [[scala.concurrent.Future]]).
   * @return If this is successful, a successful bound message should be returned.
   *         An exception will be thrown otherwise.
   */
  def startTestServer(port: Int = SpecHelper.port)(implicit system: ActorSystem, ctx: ExecutionContext): Http.Bound = synchronized {
    serverBoundMsg match {
      case None =>
        val futureBound = startServer(
          interface = "localhost",
          port = port) {
            route
          }
        val bound = Await.result(futureBound, 5.seconds)
        serverBoundMsg = Some(bound)
        bound
      case Some(boundMsg) =>
        boundMsg
    }
  }

  /**
   * Shuts down the test server.
   *
   * Does not shut down the ActorSystem the server was running on.
   *
   * Be careful when using this, it is currently coded in a way
   * that can result in race conditions (server started, another thread shuts it down,
   * previous thread assumes it was up).
   *
   * @param system ActorSystem that [[sprawler.DummyTestServer.startTestServer]] was called with.
   * @throws RuntimeException Thrown if shutdown fails.
   */
  def shutdownTestServer(implicit system: ActorSystem) = synchronized {
    implicit val timeout = Timeout(5.seconds)

    val closed = Await.result(IO(Http) ? Http.CloseAll, 5.seconds)

    if (closed == Http.ClosedAll) {
      serverBoundMsg = None
    } else {
      throw new RuntimeException("Unable to shutdown test server.")
    }
  }

  /**
   * This is the route / responses that are used with the test server.
   * See implementation for details.
   */
  val route = path("") {
    get {
      complete {
        <html>
          <body>
            <a href="/relativeUrl"></a>
            <a href="relativeUrlMissingSlash"></a>
            <a href="#relativeUrlHash"></a>
            <a href="/relativeUrl#thisShouldBeIgnored"></a>
            <a href="/redirectForever"></a>
            <a href="/redirectOnce"></a>
            <a href="http://localhost:8080/fullUri"></a>
            <a href="/nestedOnce"></a>
          </body>
        </html>
      }
    }
  } ~
    path("robots.txt") {
      get {
        complete {
          """|User-agent: *""".stripMargin
        }
      }
    } ~
    path("relativeUrl") {
      get {
        complete {
          <html>
            ok
          </html>
        }
      }
    } ~
    path("redirectForever" / IntNumber) { num =>
      get {
        redirect(s"http://localhost:8080/redirectForever/${num + 1}", StatusCodes.MovedPermanently)
      }
    } ~
    path("redirectOnce") {
      get {
        redirect(Uri("http://localhost:8080/relativeUrl"), StatusCodes.MovedPermanently)
      }
    } ~
    path("internalError") {
      get {
        respondWithStatus(StatusCodes.InternalServerError) {
          complete {
            "kaboom"
          }
        }
      }
    } ~
    path("notFound") {
      get {
        respondWithStatus(StatusCodes.NotFound) {
          complete {
            "not here"
          }
        }
      }
    } ~
    path("timeout") {
      get {
        val aPromise = Promise[String]()
        aPromise.future.map { i => "hello world:"+i }
        complete(aPromise.future)
      }
    } ~
    path("fullUri") {
      get {
        complete {
          <html>
            <body>
              fullUri
            </body>
          </html>
        }
      }
    } ~
    path("nestedOnce") {
      get {
        complete {
          <html>
            <body>
              <a href="/nestedTwice"></a>
              <a href="/relativeUrl"></a>
            </body>
          </html>
        }
      }
    } ~
    path("nestedTwice") {
      get {
        complete {
          <html>
            <body>
              ok
            </body>
          </html>
        }
      }
    } ~
    path("relativeUrl" / "folder") {
      get {
        complete {
          <html>
            <body>
              <a href="../relativeUrl"></a>
            </body>
          </html>
        }
      }
    }
}