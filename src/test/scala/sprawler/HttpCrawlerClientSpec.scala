package sprawler

import akka.actor.ActorSystem
import akka.testkit.{TestProbe, ImplicitSender, TestKit}

import sprawler.config.{CustomCrawlerConfig, CrawlerConfig}
import sprawler.CrawlerExceptions._
import sprawler.http.client.HttpCrawlerClient

import org.scalatest._
import org.scalatest.matchers.ShouldMatchers

import scala.concurrent.{ExecutionContext, Future, Await}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.{Future, Await, ExecutionContext}
import scala.concurrent.duration._
import scala.util.{Try, Failure, Success}

import spray.can.client.HostConnectorSettings
import spray.can.client.ClientConnectionSettings

import spray.http.Uri

class HttpCrawlerClientSpec(_system: ActorSystem)
  extends TestKit(_system)
  with WordSpecLike
  with BeforeAndAfter
  with BeforeAndAfterAll
  with ShouldMatchers
  with PrivateMethodTester
  with SpecHelper {

  override def beforeAll {
    DummyTestServer.startTestServer()
  }
  override def afterAll {
    DummyTestServer.shutdownTestServer(system)
  }

  // Make sure not to overwrite "CrawlerSystem" in another test without
  // also shutting down the test server first, otherwise the test server will
  // be down when the other test(s) expect it to be up.
  def this() = this(ActorSystem("CrawlerSystem"))

  "HttpCrawlerClient" when {
    ".get(path)" should {
      // TODO: use fake stubbed endpoints instead of real ones
      "fetch a simple https:// url" in {
        val request = HttpCrawlerClient(Uri("https://www.google.com")).get("/")
        val res = Await.result(request, 5.seconds)
        res.status.value shouldBe "200 OK"
      }
      "fetch a simple http:// url" in {
        val client = HttpCrawlerClient("http://www.reddit.com/")
        val request = client.get("/robots.txt")
        val response = Await.result(request, 5.seconds)
        response.status.value shouldBe "200 OK"
      }
      "fail on fetching a site that disallows crawling" in {
        val request = HttpCrawlerClient("https://github.com").get("/")
        val res = Try(Await.result(request, 5.seconds))

        res.isFailure shouldBe true

        res shouldBe Failure(UrlNotAllowedException("https://github.com", "/", UrlNotAllowedException.RobotRuleDisallowed))
      }
      "handle timeouts" in {
        val conf = CustomCrawlerConfig(crawlDelay = 50.milliseconds)

        localHttpTest(conf) { client =>
          val request = client.get("/timeout")

          val timeout = ClientConnectionSettings(system).requestTimeout.length.longValue()
          val retries = HostConnectorSettings(system).maxRetries.longValue()

          // Add extra slight delay to allow timeout failure to be returned
          val result = Try(Await.result(request, (timeout * (retries + 2)).seconds))

          result.isFailure shouldBe true
          result match {
            case p @ Failure(x) =>
              x.toString shouldBe "spray.can.Http$RequestTimeoutException: GET request to /timeout timed out"
          }
        }
      }
      "throttle requests to the same domain" in {
        val conf = CustomCrawlerConfig(crawlDelay = 100.milliseconds)
        localHttpTest(conf) { client =>
          val requestList = List(
            client.get("/robots.txt"),
            client.get("/robots.txt"),
            client.get("/robots.txt"),
            client.get("/robots.txt")
          )
          val requests = Future.sequence(requestList)
          val timeout = intercept[java.util.concurrent.TimeoutException] {
            val result = Await.result(requests, client.crawlerConfig.crawlDelay)
          }
          timeout.getMessage shouldBe s"Futures timed out after [${client.crawlerConfig.crawlDelay}]"
        }
      }
      "not throttle requests to different domains" in {
        val conf = CustomCrawlerConfig(crawlDelay = 1000.milliseconds)
        localHttpTest(conf) { client =>
          val requestList = List(
            HttpCrawlerClient("https://www.github.com").get("/robots.txt"),
            HttpCrawlerClient("https://www.youtube.com").get("/robots.txt"),
            HttpCrawlerClient("https://www.google.com").get("/robots.txt")
          )

          val requests = Future.sequence(requestList)
          // might fail if any of the requests take longer than 2 * crawlDelay
          val results = Await.result(requests, client.crawlerConfig.crawlDelay * 2)

          results.foreach { res =>
            res.status.isSuccess shouldBe true
          }
        }
      }
      "handle 400s" in {
        val conf = CustomCrawlerConfig(crawlDelay = 0.milliseconds)
        localHttpTest() { client =>
          val request = client.get("/notFound")
          val result = Await.result(request, 5.seconds)
        }
      }
      "handle 300s" in {
        val conf = CustomCrawlerConfig(crawlDelay = 0.milliseconds)
        localHttpTest() { client =>
          val request = client.get("/redirectOnce")
          val result = Await.result(request, 5.seconds)
          result.status.value shouldBe "301 Moved Permanently"
        }
      }
      "handle 500s" in {
        val conf = CustomCrawlerConfig(crawlDelay = 0.milliseconds)
        localHttpTest() { client =>
          val request = client.get("/internalError")
          val result = Await.result(request, 5.seconds)
          result.status.value shouldBe "500 Internal Server Error"
        }
      }
    }

    ".get(path, pipe)" should {
      "work" in {
        val conf = CustomCrawlerConfig(crawlDelay = 0.milliseconds)
        localHttpTest() { client =>
          val request = client.get("/", client.bodyOnlyPipeline)
          val res = Await.result(request, 5.seconds)
          res should not be " "
        }
      }
    }

    ".fetchRobotRules" should {
      "properly fetch a site with robots.txt" in {
        val conf = CustomCrawlerConfig(crawlDelay = 0.milliseconds)
        localHttpTest(conf) { client =>
          val request = client.robotRules
          val rules = Await.result(request, 5.seconds)
          rules.getCrawlDelay shouldBe 0
        }
      }
      "gracefully fail when trying to fetch a site without robots.txt" in {
        val conf = CustomCrawlerConfig(crawlDelay = 0.milliseconds)
        localHttpTest() { client =>
          val request = client.get("/fakepath/robots.txt", client.fetchRobotRules)
          val rules = Await.result(request, 5.seconds)
          rules.getCrawlDelay.milliseconds shouldBe client.crawlerConfig.crawlDelay

          Await.result(client.get("/fakepath/robots.txt"), 5.seconds)
          rules.isAllowAll shouldBe true
        }
      }
    }
  }
}