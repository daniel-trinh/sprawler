/**
 * This package is full of ADTs for http response status codes. Every response has a
 * statusCode and a color. The color is for categorizing and rendering
 */
package biz.http

sealed trait Status {
  def statusCode: Int
  def color: String
}
sealed trait Informational1xx extends Status {
  assert(statusCode >= 100 && statusCode < 200)
  val color = "grey"
}
sealed trait Success2xx extends Status {
  assert(statusCode >= 200 && statusCode < 300)
  val color = "green"
}
sealed trait Redirect3xx extends Status {
  assert(statusCode >= 300 && statusCode < 400)
  val color = "yellow"
}
sealed trait ClientError4xx extends Status {
  assert(statusCode >= 400 && statusCode < 500)
  val color = "red"
}
sealed trait ServerError5xx extends Status {
  assert(statusCode >= 500 && statusCode < 600)
  val color = "orange"
}
sealed trait UnknownStatus extends Status {
  val color = "purple"
}

case class Informational(statusCode: Int) extends Informational1xx
case class Success(statusCode: Int) extends Success2xx
case class Redirect(statusCode: Int) extends Redirect3xx
case class ClientError(statusCode: Int) extends ClientError4xx
case class ServerError(statusCode: Int) extends ServerError5xx
case class Unknown(statusCode: Int) extends UnknownStatus

case object Ok extends Success2xx {
  val statusCode = 200
}
case object Created extends Success2xx {
  val statusCode = 201
}
case object Accepted extends Success2xx {
  val statusCode = 202
}
case object NonAuthoritativeInformation extends Success2xx {
  val statusCode = 203
}
case object NoContent extends Success2xx {
  val statusCode = 204
}
case object ResetContent extends Success2xx {
  val statusCode = 205
}
case object PartialContent extends Success2xx {
  val statusCode = 206
}
case object MultiStatus extends Success2xx {
  val statusCode = 207
}
case object AlreadyReported extends Success2xx {
  val statusCode = 208
}
case object IMUsed extends Success2xx {
  val statusCode = 226
}
case object MovedPermanently extends Redirect3xx {
  val statusCode = 301
}
case object Found extends Redirect3xx {
  val statusCode = 302
}
case object SeeOther extends Redirect3xx {
  val statusCode = 303
}
case object NotModified extends Redirect3xx {
  val statusCode = 304
}
case object UseProxy extends Redirect3xx {
  val statusCode = 305
}
case object SwitchProxy extends Redirect3xx {
  val statusCode = 306
}
case object TemporaryRedirect extends Redirect3xx {
  val statusCode = 307
}
case object PermanentRedirect extends Redirect3xx {
  val statusCode = 308
}
case object BadRequest extends ClientError4xx {
  val statusCode = 400
}
case object Unauthorized extends ClientError4xx {
  val statusCode = 401
}
case object PaymentRequired extends ClientError4xx {
  val statusCode = 402
}
case object Forbidden extends ClientError4xx {
  val statusCode = 403
}
case object NotAcceptable extends ClientError4xx {
  val statusCode = 406
}
case object LengthRequired extends ClientError4xx {
  val statusCode = 411
}
case object NotFound extends ClientError4xx {
  val statusCode = 404
}
case object MethodNotAllowed extends ClientError4xx {
  val statusCode = 405
}
case object ProxyAuthenticationRequired extends ClientError4xx {
  val statusCode = 407
}
case object RequestTimeout extends ClientError4xx {
  val statusCode = 408
}
case object Conflict extends ClientError4xx {
  val statusCode = 409
}
case object Gone extends ClientError4xx {
  val statusCode = 410
}
case object PreconditionFailed extends ClientError4xx {
  val statusCode = 412
}
case object RequestEntityTooLarge extends ClientError4xx {
  val statusCode = 413
}
case object RequestURITooLong extends ClientError4xx {
  val statusCode = 414
}
case object UnsupportedMediaType extends ClientError4xx {
  val statusCode = 415
}
case object RequestedRangeNotSatisfiable extends ClientError4xx {
  val statusCode = 416
}
case object ExpectationFailed extends ClientError4xx {
  val statusCode = 417
}
case object ImATeapot extends ClientError4xx {
  val statusCode = 418
}
case object EnhanceYourCalm extends ClientError4xx {
  val statusCode = 420
}
case object UnprocessableEntity extends ClientError4xx {
  val statusCode = 422
}
case object InternalServerError extends ServerError5xx {
  val statusCode = 500
}
case object BadGateway extends ServerError5xx {
  val statusCode = 503
}