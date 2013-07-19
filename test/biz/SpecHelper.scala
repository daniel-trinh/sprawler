package biz

import biz.http.client.HttpCrawlerClient
import play.api.test.Helpers._
import play.api.test.TestServer
import scala.Some
import scala.collection.mutable
import play.api.libs.json.{ JsString, JsValue }
import play.api.libs.iteratee.Concurrent.Channel
import play.api.libs.iteratee.Input
import biz.crawler.{ CrawlerAgents, Streams }
import play.core.StaticApplication

trait SpecHelper {
  // Helper to launch test Play server and create a client for performing HTTP requests to Play server
  def localHttpTest[T](f: HttpCrawlerClient => T, port: Int = SpecHelper.port): T = {
    running(TestServer(port)) {
      val client = CrawlerAgents.getClient(
        topPrivateDomain = "localhost",
        domain = "localhost",
        portOverride = Some(port)
      )
      f(client)
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