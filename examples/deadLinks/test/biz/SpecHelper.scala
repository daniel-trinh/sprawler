package sprawler

import akka.contrib.throttle.Throttler.Rate
import akka.actor.ActorSystem

import sprawler.http.client.{ PromiseRequest, Throttler, HttpCrawlerClient }
import sprawler.crawler.Streams

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
      var closed: Boolean                      = false) extends Channel[JsValue] {
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

  trait DummyThrottler extends Throttler with WordSpecLike with ShouldMatchers {
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

      resOne shouldBe true
      resTwo shouldBe true
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

      timeoutError.getMessage shouldBe s"Futures timed out after [$duration]"
    }
  }

}