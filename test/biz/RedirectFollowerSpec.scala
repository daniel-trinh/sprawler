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

// This spec should not be run by its own, instead it should be run indirectly through ServerDependentSpecs
@DoNotDiscover
class RedirectFollowerSpec extends WordSpec with ShouldMatchers with BeforeAndAfter with SpecHelper {
  "RedirectFollower" should {
    ".followRedirects" when {
      "infinite redirects" in {
        val redirectRoute = s"${SpecHelper.testDomain}${routes.TestDummy.infiniteRedirect()}"
        val redirectUrl = AbsoluteUrl(redirectRoute, redirectRoute)
        val redirector = DummyRedirector(redirectUrl)

        localHttpTest { client =>
          val request = client.get(routes.TestDummy.infiniteRedirect().url)
          val result = Await.result(redirector.followRedirects(redirectUrl, request), 10.seconds)
          result.isFailure should be === true
          result should be === Failure(RedirectLimitReachedException(redirectUrl.fromUri.toString(), redirectUrl.uri.toString()))
        }
      }
      "single redirect" in {
          
      }
    }
  }
}

case class DummyRedirector(crawlerUrl: CrawlerUrl) extends RedirectFollower