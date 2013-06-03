package biz

import org.scalatest._
import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._
import biz.SpecHelper.DummyChannel
import play.api.libs.json.JsValue
import biz.crawler._
import controllers.routes
import biz.CrawlerExceptions.RedirectLimitReachedException
import scala.util.Failure
import play.core.StaticApplication

class RedirectFollowerSpec extends WordSpec with ShouldMatchers with BeforeAndAfter with SpecHelper {
  // Launch play app for Akka.system
  new StaticApplication(new java.io.File("."))

  "RedirectFollower" should {
    ".followRedirects" when {
      "infinite redirects" in {
        val redirectRoute = s"${SpecHelper.testDomain}/${routes.TestDummy.infiniteRedirect()}"
        val redirectUrl = AbsoluteHttpUrl(redirectRoute, redirectRoute)
        val redirector = DummyRedirector(redirectUrl)

        localHttpTest { client =>
          val request = client.get(routes.TestDummy.redirect().url)
          val result = Await.result(redirector.followRedirects(redirectUrl, request), 5.seconds)
          result.isFailure should be === true
          result should be === Failure(RedirectLimitReachedException(redirectUrl.fromUrl, redirectUrl.url))
        }
      }
      "single redirect" in {

      }
    }
  }
}

case class DummyRedirector(crawlerUrl: CrawlerUrl) extends RedirectFollower