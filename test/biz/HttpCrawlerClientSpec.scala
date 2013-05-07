import org.scalatest.{ WordSpec, FunSpec, BeforeAndAfter, BeforeAndAfterAll }
import org.scalatest.matchers.ShouldMatchers
import biz.HttpCrawlerClient
import play.api.libs.ws.WS
import play.api.test._
import play.api.test.Helpers._
import play.core.StaticApplication
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.xml.XML

class HttpCrawlerClientSpec extends WordSpec with BeforeAndAfter with ShouldMatchers {
  // Launch play app for Akka.system
  new StaticApplication(new java.io.File("."))

  "HttpCrawlerClient" when {
    ".get(path)" should {
      // TODO: use fake stubbed endpoints instead of real ones
      "fetch a simple https:// url" in {
        val request = HttpCrawlerClient("https://github.com").get("/")
        val res = Await.result(request, 5.seconds)
        res.status.value should be === 200
      }
      "fetch a simple http:// url" in {
        val client = HttpCrawlerClient("http://www.yahoo.com")
        val request = client.get("/")
        val res = Await.result(request, 5.seconds)
        res.status.value should be === 200
      }
    }

    ".get(path, pipe)" should {
      "work" in {
        running(TestServer(3333)) {
          val client = HttpCrawlerClient("localhost", portOverride = Some(3333))
          val request = client.get("/", client.bodyOnlyPipeline)
          val res = Await.result(request, 5.seconds)
        }
      }
    }
  }
}