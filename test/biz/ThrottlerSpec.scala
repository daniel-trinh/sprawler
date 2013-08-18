package biz

import org.scalatest._
import biz.http.client.{ PromiseRequest, Throttler }
import akka.contrib.throttle.Throttler.Rate
import scala.concurrent.{ Await, Promise, Future }
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._
import play.core.StaticApplication

class ThrottlerSpec extends WordSpec with ShouldMatchers with BeforeAndAfter with BeforeAndAfterAll {
  override def beforeAll() {
    // Launch play app for Akka.system
    new StaticApplication(new java.io.File("."))
  }

  "throttler" should {
    "throttle multiple actions in parallel" when {
      "throttle multiple actions A" in {
        DummyThrottler.testThrottling()
      }
      "throttle multiple actions B" in {
        DummyThrottler.testThrottling()
      }
    }

    "throttle and timeout requests that are over the delay rate" in {
      DummyThrottler.testTimeoutThrottling()
    }
  }

  object DummyThrottler extends Throttler {
    val crawlDelayRate = Future(Rate(1, 1.second))

    def testThrottling() {
      val promiseOne = Promise[Boolean]()
      val promiseTwo = Promise[Boolean]()

      val actorRef = Await.result(DummyThrottler.throttler, 5.seconds)

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

      val actorRef = Await.result(DummyThrottler.throttler, 5.seconds)

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

