import org.scalatest.{ WordSpec, FunSpec, BeforeAndAfter, BeforeAndAfterAll }
import org.scalatest.matchers.ShouldMatchers
import biz.CrawlerClient
import play.api.libs.concurrent.Execution.Implicits._
import play.core.StaticApplication
import scala.concurrent.Await
import scala.concurrent.duration._

class CrawlerClientSpec extends WordSpec with BeforeAndAfter with ShouldMatchers {
  // Launch play app
  new StaticApplication(new java.io.File("."))

  "CrawlerClient" when {
    ".get" should {
      // TODO: use fake stubbed endpoints instead of real ones
      "fetch a simple https:// url" in {
        val request = CrawlerClient("https://github.com").get("/")
        val res = Await.result(request, 5 seconds)
        res.status.value should be === 200
      }
      "fetch a simple http:// url" in {
        val request = CrawlerClient("http://www.yahoo.com").get("/")
        val res = Await.result(request, 5 seconds)
        res.status.value should be === 200
      }
    }
  }
}