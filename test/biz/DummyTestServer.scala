package biz

import spray.routing.SimpleRoutingApp
import spray.http.{ Uri, StatusCodes }

import akka.actor.ActorSystem
import scala.util.{ Success, Random }
import scala.concurrent.{ Await, ExecutionContext, Promise, Future }
import scala.concurrent.duration._
import spray.can.Http

object DummyTestServer extends SimpleRoutingApp {

  private var serverBoundMsg: Option[Http.Bound] = None

  def startTestServer(implicit system: ActorSystem, ctx: ExecutionContext): Http.Bound = {
    serverBoundMsg match {
      case None =>
        val futureBound = startServer(
          interface = "localhost",
          port = 8080) {
            route
          }
        val bound = Await.result(futureBound, 5.seconds)
        serverBoundMsg = Some(bound)

        bound
      case Some(boundMsg) =>
        boundMsg
    }
  }

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