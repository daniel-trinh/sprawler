package biz

import org.scalatest._
import biz.http.client.{ PromiseRequest, Throttler }
import akka.contrib.throttle.Throttler.Rate
import scala.concurrent.{ Await, Promise, Future }
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._
import play.core.StaticApplication
import biz.SpecHelper.DummyThrottler

// This spec should not be run by its own, instead it should be run indirectly through ServerDependentSpecs
@DoNotDiscover
class ThrottlerSpec extends WordSpec with ShouldMatchers with BeforeAndAfter with DummyThrottler {
  "throttler" should {
    "throttle multiple actions in parallel" when {
      "throttle multiple actions A" in {
        testThrottling()
      }
      "throttle multiple actions B" in {
        testThrottling()
      }
    }
    "throttle and timeout requests that are over the delay rate" in {
      testTimeoutThrottling()
    }
  }
}