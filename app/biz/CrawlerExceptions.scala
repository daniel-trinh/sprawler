package biz

import play.api.libs.json._
import play.api.libs.functional.syntax._
import biz.config.CrawlerConfig

/**
 * Exceptions used for this webcrawler project
 */
object CrawlerExceptions {

  object JsonImplicits {

    implicit val failedHttpRequest = (
      (__ \ "status").format[Int] and
      (__ \ "reason").format[String] and
      (__ \ "message").format[String] and
      (__ \ "errorType").format[String]
    )(FailedHttpRequestException.apply, unlift(FailedHttpRequestException.unapply))

    implicit val urlNotAllowed = (
      (__ \ "host").format[String] and
      (__ \ "path").format[String] and
      (__ \ "message").format[String] and
      (__ \ "errorType").format[String]
    )(UrlNotAllowedException.apply, unlift(UrlNotAllowedException.unapply))

    implicit val unprocessableUrl = (
      (__ \ "fromUrl").format[String] and
      (__ \ "toUrl").format[String] and
      (__ \ "message").format[String] and
      (__ \ "errorType").format[String]
    )(UnprocessableUrlException.apply, unlift(UnprocessableUrlException.unapply))

    implicit val redirectLimitReached = (
      (__ \ "fromUrl").format[String] and
      (__ \ "toUrl").format[String] and
      (__ \ "message").format[String] and
      (__ \ "maxRedirects").format[Int] and
      (__ \ "errorType").format[String]
    )(RedirectLimitReachedException.apply, unlift(RedirectLimitReachedException.unapply))

    implicit val missingRedirectUrl = (
      (__ \ "fromUrl").format[String] and
      (__ \ "message").format[String] and
      (__ \ "errorType").format[String]
    )(MissingRedirectUrlException.apply, unlift(MissingRedirectUrlException.unapply))

    implicit val unknown = (
      (__ \ "message").format[String] and
      (__ \ "errorType").format[String]
    )(UnknownException.apply, unlift(UnknownException.unapply))
  }

  /**
   * Throw this error if there is a problem with a [[spray.http.HttpResponse]].
   * @param status
   * @param reason
   * @param message
   * @param errorType
   */
  case class FailedHttpRequestException(
    status: Int,
    reason: String,
    message: String,
    errorType: String = "failed_http_request") extends CrawlerException(message)

  /**
   * Throw this if a URL cannot be crawled due to restrictions in robots.txt.
   * @param host
   * @param path
   * @param message
   * @param errorType
   */
  case class UrlNotAllowedException(
    host: String,
    path: String,
    message: String,
    errorType: String = "url_not_allowed") extends CrawlerException(message)

  object UrlNotAllowedException {
    val RobotRuleDisallowed = "This domain's robot rules does not allow crawling of this url."
  }

  /**
   * Throw this if a URL cannot be crawled due to a malformed URL.
   * @param fromUrl
   * @param toUrl
   * @param message
   * @param errorType
   */
  case class UnprocessableUrlException(
    fromUrl: String,
    toUrl: String,
    message: String,
    errorType: String = "unprocessable_url") extends CrawlerException(message)

  object UnprocessableUrlException {
    val MissingHttpPrefix = "Url must start with https:// or http://"
  }

  /**
   * Throw this if the maximum redirects has been reached when followin 3xx redirects
   * @param fromUrl
   * @param toUrl
   * @param message
   * @param maxRedirects
   * @param errorType
   */
  case class RedirectLimitReachedException(
    fromUrl: String,
    toUrl: String,
    message: String = "Redirect limit reached",
    maxRedirects: Int = CrawlerConfig.maxRedirects,
    errorType: String = "redirect_limit_reached") extends CrawlerException(message)

  case class MissingRedirectUrlException(
    fromUrl: String,
    message: String,
    errorType: String = "missing_redirect_url") extends CrawlerException(message)

  /**
   * Throw this if you have no idea what happened.
   * @param message
   * @param errorType
   */
  case class UnknownException(
    message: String,
    errorType: String = "unknown") extends CrawlerException(message)
}

sealed class CrawlerException(message: String) extends RuntimeException(message)