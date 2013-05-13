import biz.config.{ SprayCanConfig, CrawlerConfig }
import org.scalatest._
import org.scalatest.matchers.ShouldMatchers
import biz.{ RobotRules, HttpClientPipelines, HttpCrawlerClient, Client }
import play.api.test.Helpers._
import play.api.test.TestServer
import play.core.StaticApplication
import biz.CustomExceptions._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.Some
import scala.util.{ Try, Failure, Success }
import spray.can.client.ClientSettings

class HttpCrawlerClientSpec extends WordSpec with BeforeAndAfter with ShouldMatchers with PrivateMethodTester {
  // Launch play app for Akka.system
  new StaticApplication(new java.io.File("."))

  "HttpCrawlerClient" when {
    ".get(path)" should {
      // TODO: use fake stubbed endpoints instead of real ones
      "fetch a simple https:// url" in {
        val request = HttpCrawlerClient("https://www.google.com").get("/")
        val res = Await.result(request, 5.seconds)

        res.isSuccess should be === true

        res map { response =>
          response.status.value should be === 200
        }
      }
      "fetch a simple http:// url" in {
        val client = HttpCrawlerClient("http://www.yahoo.com")
        val request = client.get("/")
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

        res should be === Failure(UrlNotAllowed("https://github.com", "/", UrlNotAllowed.RobotRuleDisallowed))
      }
      "handle timeouts" in {
        running(TestServer(3333)) {
          val client = HttpCrawlerClient("localhost", portOverride = Some(3333))
          val request = client.get("/test/timeout")

          println(ClientSettings().RequestTimeout)
          // Set to 1 second more than spray.can.client: request-timeout
          val long: Long = 1
          val result = Await.result(request, ClientSettings().RequestTimeout.longValue().seconds)
          result.isFailure should be === true
          result match {
            case p @ Failure(x) => println(p)
          }
        }
      }
      "handle 400s" in {
        running(TestServer(3333)) {
          val client = HttpCrawlerClient("localhost", portOverride = Some(3333))
          val request = client.get("/test/notFound")
          val result = Await.result(request, 5.seconds)
        }
      }
      "handle 300s" in {
        running(TestServer(3333)) {
          val client = HttpCrawlerClient("localhost", portOverride = Some(3333))
          val request = client.get("/test/redirect")
          val result = Await.result(request, 5.seconds)
          result map { res =>
            println(res.entity.asString)
          }
        }
      }
      "handle 500s" in {
        running(TestServer(3333)) {
          val client = HttpCrawlerClient("localhost", portOverride = Some(3333))
          val request = client.get("/test/internalError")
          val result = Await.result(request, 5.seconds)
        }
      }
    }

    ".get(path, pipe)" should {

      "work" in {
        running(TestServer(3333)) {
          val client = HttpCrawlerClient("localhost", portOverride = Some(3333))
          val request = client.get("/", client.bodyOnlyPipeline)
          val res = Await.result(request, 5.seconds)

          res.isFailure should be === false
        }
      }
    }

    ".fetchRobotRules" should {
      "properly fetch a site with robots.txt" in {
        running(TestServer(3333)) {
          val client = HttpCrawlerClient("localhost", portOverride = Some(3333))
          val request = client.robotRules
          val tryRules = Await.result(request, 5.seconds)
          tryRules.isSuccess should be === true
          tryRules map { rules =>
            rules.getCrawlDelay should be === 11 * 1000
          }
        }
      }
      "gracefully fail when trying to fetch a site without robots.txt" in {
        running(TestServer(3333)) {
          val client = HttpCrawlerClient("localhost", portOverride = Some(3333))
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