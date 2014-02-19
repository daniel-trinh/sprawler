package sprawler

import org.scalatest._

import sprawler.crawler.Streams
import sprawler.CrawlerExceptions.UrlNotAllowedException
import sprawler.SpecHelper.DummyStream

import play.api.libs.iteratee.{ Input, Iteratee, Concurrent }
import play.api.libs.json.{ JsNumber, JsObject, JsString, JsValue }
import play.api.libs.iteratee.Concurrent.Channel

import scala.collection.mutable
import spray.http.HttpResponse

class StreamsSpec extends WordSpec with ShouldMatchers with BeforeAndAfter {
  "Streams" when {

    var dummyStream: DummyStream = null
    before {
      dummyStream = new DummyStream
    }
    ".streamJson" should {
      "push json and not close the channel" in {
        dummyStream.streamJson(JsString("Input"))
        dummyStream.channel.bucket shouldBe mutable.ArrayBuffer(JsString("Input"))
      }
    }

    ".streamJsonResponse" should {
      "push an http response and not close the channel" in {
        val response = HttpResponse()
        dummyStream.streamJsonResponse("url1", "url2", HttpResponse())
        dummyStream.channel.bucket shouldBe mutable.ArrayBuffer(
          JsObject(
            Seq(
              "from_url" -> JsString("url1"),
              "to_url" -> JsString("url2"),
              "status" -> JsNumber(response.status.intValue),
              "reason" -> JsString(response.status.reason)
            )
          )
        )
      }
    }

    ".streamJsonErrorFromException" should {
      "push json and close the channel" in {
        dummyStream.streamJsonError(JsString("error happened"))
        dummyStream.channel.bucket shouldBe mutable.ArrayBuffer(
          JsObject(
            Seq("error" -> JsString("error happened"))
          ),
          JsString("EOF")
        )
      }
    }
    ".streamJsonErrorFromException" should {
      "push an exception, convert it to json, and close the channel" in {
        dummyStream.streamJsonErrorFromException(UrlNotAllowedException("host", "path", "url sucks"), eof = true)
        dummyStream.channel.bucket shouldBe mutable.ArrayBuffer(
          JsObject(
            Seq(
              "error" -> JsObject(Seq(
                "host" -> JsString("host"),
                "path" -> JsString("path"),
                "message" -> JsString("url sucks"),
                "errorType" -> JsString("url_not_allowed")
              ))
            )
          ),
          JsString("EOF")
        )
      }
    }
  }
}