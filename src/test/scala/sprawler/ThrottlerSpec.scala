package sprawler

import akka.testkit.{ TestProbe, ImplicitSender, TestKit }
import akka.contrib.throttle.Throttler.Rate
import akka.actor.ActorSystem

import sprawler.http.client.{ PromiseRequest, Throttler }
import sprawler.SpecHelper.DummyThrottler

import org.scalatest._

import scala.concurrent.{ Await, Promise, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ThrottlerSpec(_system: ActorSystem) extends TestKit(_system)
    with WordSpecLike
    with ShouldMatchers
    with BeforeAndAfter
    with BeforeAndAfterAll
    with DummyThrottler {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  def this() = this(ActorSystem("CrawlerSystem"))

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