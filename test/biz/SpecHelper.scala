package biz

import akka.contrib.throttle.Throttler.Rate
import akka.actor.ActorSystem

import biz.http.client.{ PromiseRequest, Throttler, HttpCrawlerClient }
import biz.crawler.{ CrawlerAgents, Streams }

import scala.collection.mutable
import org.scalatest._

import play.api.libs.json.{ JsString, JsValue }
import play.api.libs.iteratee.Concurrent.Channel
import play.api.libs.iteratee.Input
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.JsString
import play.api.test.{ FakeApplication, TestServer }

import spray.http.Uri
import scala.concurrent.duration._
import scala.concurrent.{ Await, Promise, Future }

trait SpecHelper {
  implicit val system: ActorSystem = ActorSystem("CrawlerSystem")

  import SpecHelper.{ serverStarted, testServer }

  /**
   * Helper to launch test Play server and create a client for performing HTTP requests to Play server.
   * Will start an accompanying FakeApplication (with Akka.system) to go along with the server.
   *
   * WARNING: the test Play server launched here is not sandboxed per usage -- it is shared
   * and reused across every instance that this method is called. Call <code>shutdownTestServer</code>
   * to shut down the server that this method uses.
   *
   * @param f The code to execute with the localhost-scoped [[biz.http.client.HttpCrawlerClient]].
   * @param port The port to run the server on.
   * @tparam T Return type of the code that gets executed with f. Can be Unit.
   */
  def localHttpTest[T](f: HttpCrawlerClient => T, port: Int = SpecHelper.port): T = {
    startAndExecuteServer(port) {
      executeWithinClient(f, port)
    }
  }

  private def executeWithinClient[T](f: HttpCrawlerClient => T, port: Int = SpecHelper.port): T = {
    val uri = Uri(s"http://localhost:$port")
    val client = CrawlerAgents.retrieveClient(
      uri = uri
    )
    // This would normally be bad code because of the blocking Await, but this is only supposed
    // to be used for specs.
    f(Await.result(client, 5.seconds))
  }

  private def printUri(uri: Uri) {
    println(s"uri:$uri")
    println(s"scheme:${uri.scheme}")
    println(s"port:${uri.authority.port}")
  }

  /**
   * Executes a block of code in a running server.
   */
  def startAndExecuteServer[T](port: Int)(block: => T): T = {
    synchronized {
      try {
        if (!serverStarted) {
          testServer = TestServer(port)
          testServer.start()
          serverStarted = true
        }
        block
      }
    }
  }

  /**
   * Shuts down the testServer. This should be run after all tests are done with using the server.
   */
  def shutdownTestServer() {
    play.Logger.debug("test server going down")
    if (testServer != null) {
      testServer.stop()
      serverStarted = false
    }
  }
}

object SpecHelper {

  private[SpecHelper] var serverStarted = false
  private[SpecHelper] var testServer: TestServer = null

  val port = 3333

  /**
   * Should match up with url of Play running in test mode, but syncing is not guaranteed.
   * This val is just for convenience.
   */
  val testDomain = s"http://localhost:$port"

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

  trait DummyThrottler extends Throttler with WordSpec with ShouldMatchers {
    val system = ActorSystem("ThrottlerSystem")

    val crawlDelayRate = Future.successful(Rate(1, 1.second))

    def testThrottling() {
      val promiseOne = Promise[Boolean]()
      val promiseTwo = Promise[Boolean]()

      val actorRef = Await.result(throttler, 5.seconds)

      actorRef ! PromiseRequest(promiseOne)
      actorRef ! PromiseRequest(promiseTwo)

      val resOne = Await.result(promiseOne.future, 5.seconds)
      val resTwo = Await.result(promiseTwo.future, 5.seconds)

      resOne should be === true
      resTwo should be === true
    }

    def testTimeoutThrottling() {
      val promiseOne = Promise[Boolean]()
      val promiseTwo = Promise[Boolean]()

      val actorRef = Await.result(throttler, 5.seconds)

      actorRef ! PromiseRequest(promiseOne)
      actorRef ! PromiseRequest(promiseTwo)

      val duration = 1.microsecond
      val timeoutError = intercept[java.util.concurrent.TimeoutException] {
        Await.result(promiseTwo.future, duration)
      }

      timeoutError.getMessage should be === s"Futures timed out after [$duration]"
    }
  }

}