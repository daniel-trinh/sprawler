package sprawler

import akka.contrib.throttle.Throttler.Rate
import akka.actor.ActorSystem

import sprawler.http.client.{ PromiseRequest, Throttler, HttpCrawlerClient }
import sprawler.crawler.CrawlerSession
import sprawler.config._

import scala.collection.mutable
import org.scalatest._

import play.api.libs.json.{ JsString, JsValue }
import play.api.libs.json.JsString

import spray.http.Uri
import scala.concurrent.duration._
import scala.concurrent.{ Await, Promise, Future }
import scala.concurrent.ExecutionContext.Implicits.global

trait SpecHelper {
  implicit val system: ActorSystem

  /**
   * Helper to launch [[sprawler.DummyTestServer]] server and create a client for performing HTTP requests to test server.
   *
   * @param f The code to execute with the localhost-scoped [[sprawler.http.client.HttpCrawlerClient]].
   * @param port The port to run the server on.
   * @tparam T Return type of the code that gets executed with f. Can be Unit.
   */
  def localHttpTest[T](crawlerConfig: CrawlerConfig = DefaultCrawlerConfig)(f: HttpCrawlerClient => T, port: Int = SpecHelper.port): T = {
    startAndExecuteServer(port) {
      executeWithinClient(crawlerConfig, f, port)
    }
  }

  private def executeWithinClient[T](crawlerConfig: CrawlerConfig, f: HttpCrawlerClient => T, port: Int = SpecHelper.port): T = {
    val uri = Uri(s"http://localhost:$port")

    val client = HttpCrawlerClient(uri, crawlerConfig)

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
  def startAndExecuteServer[T](port: Int)(block: => T): T = {
    DummyTestServer.startTestServer(port)
    block
  }
}

object SpecHelper {

  val port = 8080

  /**
   * Should match up with url of Play running in test mode, but syncing is not guaranteed.
   * This val is just for convenience.
   */
  val testDomain = s"http://localhost:$port"

  trait DummyThrottler extends Throttler with WordSpecLike with ShouldMatchers {
    implicit val system: ActorSystem

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