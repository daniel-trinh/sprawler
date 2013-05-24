package biz

import biz.config.CrawlerConfig
import biz.CrawlerExceptions._
import org.scalatest._
import org.scalatest.matchers.ShouldMatchers
import play.api.test.Helpers._
import play.api.test.TestServer
import play.core.StaticApplication
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.{ Future, Await }
import scala.concurrent.duration._
import scala.Some
import scala.util.{ Try, Failure, Success }
import spray.can.client.ClientSettings
import spray.client.ConduitSettings
import biz.http.client.HttpCrawlerClient

class HttpCrawlerClientSpec extends WordSpec with BeforeAndAfter with ShouldMatchers with PrivateMethodTester {
  // Launch play app for Akka.system
  new StaticApplication(new java.io.File("."))

  // Helper to launch test Play server and create a client for performing HTTP requests to Play server
  def localHttpTest[T](f: HttpCrawlerClient => T, port: Int = 3333): T = {
    running(TestServer(port)) {
      val client = HttpCrawlerClient("localhost", portOverride = Some(port))
      f(client)
    }
  }

  "HttpCrawlerClient" when {
    ".get(path)" should {
      // TODO: use fake stubbed endpoints instead of real ones
      "fetch a simple https:// url" in {
        val request = HttpCrawlerClient("https://www.google.com").get("/robots.txt")
        val res = Await.result(request, 5.seconds)

        res.isSuccess should be === true

        res map { response =>
          response.status.value should be === 200
        }
      }
      "fetch a simple http:// url" in {
        val client = HttpCrawlerClient("http://www.yahoo.com")
        val request = client.get("/robots.txt")
        val res = Await.result(request, 5.seconds)

        res.isSuccess should be === true

        res map { response =>
          response.status.value should be === 200
        }
      }
      "fail on fetching a site that disallows crawling" in {
        val request = HttpCrawlerClient("https://github.com").get("/")
        val res = Await.result(request, 5.seconds)

        res.isFailure should be === true

        res should be === Failure(UrlNotAllowedException("https://github.com", "/", UrlNotAllowedException.RobotRuleDisallowed))
      }
      "handle timeouts" in {
        localHttpTest { client =>
          val request = client.get("/test/timeout")

          // Add extra slight delay to allow timeout failure to be returned
          val timeout = ClientSettings().RequestTimeout.longValue()
          val retries = ConduitSettings().MaxRetries.longValue()

          val result = Await.result(request, (timeout * (retries + 2)).milliseconds)
          result.isFailure should be === true
          result match {
            case p @ Failure(x) =>
              x.toString should be === "java.lang.RuntimeException: Connection closed, reason: IdleTimeout"
          }
        }
      }
      "throttle requests to the same domain" in {
        localHttpTest { client =>
          val requestList = List(
            client.get("/robots.txt"),
            client.get("/robots.txt"),
            client.get("/robots.txt")
          )
          val requests = Future.sequence(requestList)
          val timeout = intercept[java.util.concurrent.TimeoutException] {
            val result = Await.result(requests, CrawlerConfig.defaultCrawlDelay.milliseconds)
          }
          timeout.getMessage should be === s"Futures timed out after [${CrawlerConfig.defaultCrawlDelay} milliseconds]"
        }
      }
      "not throttle requests to different domains" in {
        localHttpTest { client =>
          val requestList = List(
            client.get("/robots.txt"),
            HttpCrawlerClient("https://www.github.com").get("/robots.txt"),
            HttpCrawlerClient("https://www.youtube.com").get("/robots.txt"),
            HttpCrawlerClient("https://www.google.com").get("/robots.txt")
          )

          val requests = Future.sequence(requestList)
          // might fail if any of the requests take longer than 2 * defaultCrawlDelay
          val results = Await.result(requests, CrawlerConfig.defaultCrawlDelay.milliseconds * 2)

          results.foreach { res =>
            res.isSuccess should be === true
            res foreach { res =>
              res.status.isSuccess should be === true
            }
          }
        }
      }
      "handle 400s" in {
        localHttpTest { client =>
          val request = client.get("/test/notFound")
          val result = Await.result(request, 5.seconds)
        }
      }
      "handle 300s" in {
        localHttpTest { client =>
          val request = client.get("/test/redirect")
          val result = Await.result(request, 5.seconds)
          result map { res =>
            println(res.entity.asString)
          }
        }
      }
      "handle 500s" in {
        localHttpTest { client =>
          val request = client.get("/test/internalError")
          val result = Await.result(request, 5.seconds)
        }
      }
    }

    ".get(path, pipe)" should {

      "work" in {
        localHttpTest { client =>
          val request = client.get("/", client.bodyOnlyPipeline)
          val res = Await.result(request, 5.seconds)

          res.isFailure should be === false
        }
      }
    }

    ".fetchRobotRules" should {
      "properly fetch a site with robots.txt" in {
        localHttpTest { client =>
          val request = client.robotRules
          val tryRules = Await.result(request, 5.seconds)
          tryRules.isSuccess should be === true
          tryRules map { rules =>
            rules.getCrawlDelay should be === 1 * 1000
          }
        }
      }
      "gracefully fail when trying to fetch a site without robots.txt" in {
        localHttpTest { client =>
          val request = client.get("/fakepath/robots.txt", client.fetchRobotRules)
          val tryRules = Await.result(request, 5.seconds)
          tryRules.isSuccess should be === true
          tryRules map { rules =>
            rules.getCrawlDelay should be === CrawlerConfig.defaultCrawlDelay
            rules.isAllowAll should be === true
          }

        }
      }
    }
  }
}