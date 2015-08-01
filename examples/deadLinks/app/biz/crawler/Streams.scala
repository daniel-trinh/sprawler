package sprawler.crawler

import akka.http.scaladsl.model.HttpResponse
import org.json4s.jackson.Serialization
import sprawler.CrawlerExceptions._
import sprawler.CrawlerException

///**
// * For pushing JSON results and exceptions into a [[play.api.libs.iteratee.Concurrent.Channel]].
// */
//trait Streams {
//
//  def channel: Concurrent.Channel[JsValue]
//
//  def streamJson[T](json: T, eof: Boolean = false) {
//    channel push Serialization.write[T](json)
//
//    if (eof) {
//      play.Logger.debug("channel is closing....")
//      channel.push(Input.EOF)
//    }
//  }
//
//  def streamJsonError(jsError: JsValue, eof: Boolean = true) {
//    streamJson(JsObject(Seq("error" -> jsError)), eof)
//  }
//
//  def streamJsonResponse(fromUrl: String, toUrl: String, response: HttpResponse, eof: Boolean = false) {
//    streamJson(responseToJson(fromUrl, toUrl, response), eof)
//  }
//
//  /**
//   * Streams the json representation of a crawler exception. Crawler exceptions
//   * are defined in [[sprawler.CrawlerException]].
//   *
//   * @param error The throwable to convert into json and stream.
//   */
//  def streamJsonErrorFromException(error: Throwable, eof: Boolean = false) {
//    error match {
//      case e: CrawlerException => e match {
//        // NOTE: this code is left in this verbose manner, because Json.toJson doesn't work
//        // when trying to use this shortened version, due to the type inference getting generalized to
//        // "Throwable": http://stackoverflow.com/a/8481924/1093160. This should at least
//        // throw a compiler warning when a CrawlerException type is not matched, since CrawlerException
//        // is sealed.
//        case error @ UnprocessableUrlException(_, _, _, _) =>
//          streamJsonError(Serialization.write(error), eof)
//        case error @ FailedHttpRequestException(_, _, _, _) =>
//          streamJsonError(Serialization.write(error), eof)
//        case error @ UrlNotAllowedException(_, _, _, _) =>
//          streamJsonError(Serialization.write(error), eof)
//        case error @ RedirectLimitReachedException(_, _, _, _, _) =>
//          streamJsonError(Serialization.write(error), eof)
//        case error @ MissingRedirectUrlException(_, _, _) =>
//          streamJsonError(Serialization.write(error), eof)
//        case error @ UnknownException(_, _) =>
//          play.Logger.error(error.getStackTraceString)
//          streamJsonError(Serialization.write(error), eof)
//      }
//      case e: Throwable =>
//        streamJsonErrorFromException(UnknownException(e.getMessage))
//        play.Logger.error(error.getStackTraceString)
//    }
//  }
//
//  /**
//   * Converts a [[spray.http.HttpResponse]] to a [[play.api.libs.json.JsValue]] for streaming
//   * to the client.
//   *
//   * @param fromUrl The origin website's url where toUrl was found on.
//   * @param toUrl The url that was crawled -- the url that was used to retrive response.
//   * @param response This gets serialized into json for streaming back to the user.
//   * @return The response in json form.
//   */
//  def responseToJson(fromUrl: String, toUrl: String, response: HttpResponse): JsValue = {
//    val status = response.status
//    val json = JsObject(
//      Seq(
//        "status" -> JsNumber(status.intValue),
//        "reason" -> JsString(status.reason),
//        "to_url" -> JsString(toUrl),
//        "from_url" -> JsString(fromUrl)
//      )
//    )
//    json
//  }
//}