package biz.crawler

import biz.CrawlerExceptions.{ UnknownException, UrlNotAllowedException, FailedHttpRequestException, UnprocessableUrlException }
import biz.CrawlerExceptions.JsonImplicits._
import play.api.libs.iteratee.Concurrent
import play.api.libs.json._
import spray.http.HttpResponse

/**
 * For pushing JSON results and exceptions into a [[play.api.libs.iteratee.Concurrent.Channel]].
 */
trait Streams {

  def channel: Concurrent.Channel[JsValue]

  def streamJson(json: JsValue, eofAndEnd: Boolean = false) {
    channel push json

    if (eofAndEnd) {
      cleanup()
    }
  }

  def streamJsonError(jsError: JsValue, eofAndEnd: Boolean = true) {
    streamJson(JsObject(Seq("error" -> jsError)), eofAndEnd)
  }

  def streamJsResponse(fromUrl: String, toUrl: String, response: HttpResponse, eofAndEnd: Boolean = false) {
    streamJson(responseToJson(fromUrl, toUrl, response), eofAndEnd)
  }

  /**
   * Streams the json representation of a crawler exception. Crawler exceptions
   * are defined in [[biz.CrawlerException]].
   *
   * @param error The throwable to convert into json and stream.
   */
  def streamJsonErrorFromException(error: Throwable) {
    error match {
      // NOTE: this code is left in this verbose manner, because Json.toJson doesn't work
      // when trying to use this shortened version, due to the type inference getting generalized to
      // "Throwable": http://stackoverflow.com/a/8481924/1093160
      case error @ UnprocessableUrlException(_, _, _, _) =>
        streamJsonError(Json.toJson(error))
      case error @ FailedHttpRequestException(_, _, _, _) =>
        streamJsonError(Json.toJson(error))
      case error @ UrlNotAllowedException(_, _, _, _) =>
        streamJsonError(Json.toJson(error))
      case error @ UnknownException(_, _) =>
        streamJsonError(Json.toJson(error))
      case error: Throwable =>
        streamJsonError(Json.toJson(UnknownException(error.getMessage)))
        // Log this error, because something unexpected happened
        play.Logger.error(error.getStackTraceString)
    }
  }

  /**
   * Converts a [[spray.http.HttpResponse]] to a [[play.api.libs.json.JsValue]] for streaming
   * to the client.
   *
   * @param fromUrl The origin website's url where toUrl was found on.
   * @param toUrl The url that was crawled -- the url that was used to retrive response.
   * @param response This gets serialized into json for streaming back to the user.
   * @return The response in json form.
   */
  def responseToJson(fromUrl: String, toUrl: String, response: HttpResponse): JsValue = {
    val status = response.status
    val json = JsObject(
      Seq(
        "status" -> JsNumber(status.value),
        "reason" -> JsString(status.reason),
        "to_url" -> JsString(toUrl),
        "from_url" -> JsString(fromUrl)
      )
    )
    json
  }

  def cleanup() {
    channel.eofAndEnd()
  }

}