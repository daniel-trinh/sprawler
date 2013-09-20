package biz

import biz.CrawlerExceptions.RedirectLimitReachedException
import biz.SpecHelper.DummyChannel
import biz.crawler._
import biz.crawler.url.{ CrawlerUrl, AbsoluteUrl }
import biz.http.client.RedirectFollower

import controllers.routes

import org.scalatest._

import play.api.libs.json.JsValue

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{ Try, Success, Failure }

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
          val result = Try(Await.result(redirector.followRedirects(redirectUrl, request), 20.seconds))
          result.isFailure should be === true
          result should be === Failure(RedirectLimitReachedException(redirectUrl.fromUri.toString(), redirectUrl.uri.toString()))
        }
      }
      "single redirect" in {
        val redirectRoute = s"${SpecHelper.testDomain}${routes.TestDummy.redirect()}"
        val redirectUrl = AbsoluteUrl(redirectRoute, redirectRoute)
        val redirector = DummyRedirector(redirectUrl)

        localHttpTest { client =>
          val request = client.get(routes.TestDummy.redirect().url)
          val result = Await.result(redirector.followRedirects(redirectUrl, request), 5.seconds)
          result.status.intValue should be === 200
        }
      }
    }
  }
}

case class DummyRedirector(originCrawlerUrl: CrawlerUrl) extends RedirectFollower