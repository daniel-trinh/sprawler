package biz

import spray.routing.SimpleRoutingApp
import spray.http.StatusCodes

import akka.actor.ActorSystem

object DummyTestServer extends SimpleRoutingApp {

  def startTestServer(implicit system: ActorSystem) = startServer(interface = "localhost", port = 8080) {
    path("") {
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
      path("redirectForever") {
        redirect("http://localhost:8080/redirectForever", StatusCodes.MovedPermanently)
      } ~
      path("redirectOnce") { path =>
        redirect("http://localhost:8080/relativeUrl", StatusCodes.MovedPermanently)
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
}