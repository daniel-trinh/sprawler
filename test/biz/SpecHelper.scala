package biz

import biz.http.client.HttpCrawlerClient
import play.api.test._
import play.api.test.Helpers._
import scala.Some
import scala.collection.mutable
import play.api.libs.json.{ JsString, JsValue }
import play.api.libs.iteratee.Concurrent.Channel
import play.api.libs.iteratee.Input
import biz.crawler.{ CrawlerAgents, Streams }
import play.core.StaticApplication
import spray.http.Uri

trait SpecHelper {

  private var serverStarted = false
  private var testServer: TestServer = null

  // Helper to launch test Play server and create a client for performing HTTP requests to Play server
  def localHttpTest[T](f: HttpCrawlerClient => T, port: Int = SpecHelper.port): T = {
    if (!serverStarted) {
      testServer = TestServer(port)
      startAndExecute(testServer) {
        serverStarted = true
        executeWithinClient(f, port)
      }
    } else {
      executeWithinClient(f, port)
    }
  }

  private def executeWithinClient[T](f: HttpCrawlerClient => T, port: Int = SpecHelper.port): T = {
    val uri = Uri(s"http://localhost:$port")
    val client = CrawlerAgents.getClient(
      uri = uri
    )
    f(client)
  }

  private def printUri(uri: Uri) {
    println(s"uri:$uri")
    println(s"scheme:${uri.scheme}")
    println(s"port:${uri.authority.port}")
  }

  /**
   * Executes a block of code in a running server.
   */
  private def startAndExecute[T](testServer: TestServer)(block: => T): T = {
    synchronized {
      try {
        testServer.start()
        block
      }
    }
  }

  /**
   * Shuts down the testServer. This should be run after all tests are done with using the server.
   */
  def shutdownTestServer() {
    if (testServer != null) {
      testServer.stop()
    }
  }
}

object SpecHelper {

  val port = 3333

  /**
   * Should match up with url of Play running in test mode, but syncing is not guaranteed.
   * This val is just for convenience.
   */
  val testDomain = s"localhost"

  /**
   * Used for testing, implements the methods for Channel.
   * @param bucket Contains values pushed into the channel
   * @param closed Whether or not the channel has received an Input.EOF
   */
  class DummyChannel(
      var bucket: mutable.ArrayBuffer[JsValue],
      var closed: Boolean = false) extends Channel[JsValue] {
    def push(chunk: Input[JsValue]) {
      chunk match {
        case Input.El(e) => bucket.append(e)
        case Input.Empty => bucket
        case Input.EOF   => bucket.append(JsString("EOF"))
      }
    }
    def end() {
      closed = true
    }
    def end(e: Throwable) {
      ()
    }
  }

  class DummyStream extends Streams {
    lazy val channel = new DummyChannel(new mutable.ArrayBuffer[JsValue]())
  }
}