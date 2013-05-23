package biz.http

// Object for storing information about an Http Response.
case class HttpResponse(statusCode: Int, body: String, headers: String) {
  // Returns a categorized status w/ information
  // about the type
  lazy val status: Status = {
    statusCode match {
      case 200 => Ok
      case 201 => Created
      case 202 => Accepted
      case 203 => NonAuthoritativeInformation
      case 204 => NoContent
      case 205 => ResetContent
      case 206 => PartialContent
      case 207 => MultiStatus
      case 208 => AlreadyReported
      case 226 => IMUsed
      case 301 => MovedPermanently
      case 302 => Found
      case 303 => SeeOther
      case 304 => NotModified
      case 305 => UseProxy
      case 306 => SwitchProxy
      case 307 => TemporaryRedirect
      case 308 => PermanentRedirect
      case 400 => BadRequest
      case 401 => Unauthorized
      case 402 => PaymentRequired
      case 403 => Forbidden
      case 406 => NotAcceptable
      case 411 => LengthRequired
      case 404 => NotFound
      case 405 => MethodNotAllowed
      case 407 => ProxyAuthenticationRequired
      case 408 => RequestTimeout
      case 409 => Conflict
      case 410 => Gone
      case 412 => PreconditionFailed
      case 413 => RequestEntityTooLarge
      case 414 => RequestURITooLong
      case 415 => UnsupportedMediaType
      case 416 => RequestedRangeNotSatisfiable
      case 417 => ExpectationFailed
      case 418 => ImATeapot
      case 420 => EnhanceYourCalm
      case 422 => UnprocessableEntity
      case 500 => InternalServerError
      case 503 => BadGateway
      case num => {
        if (num > 100) {
          if (num < 200) {
            Informational(num)
          } else if (num < 300) {
            Success(num)
          } else if (num < 400) {
            Redirect(num)
          } else if (num < 500) {
            ClientError(num)
          } else if (num < 600) {
            ServerError(num)
          } else {
            Unknown(num)
          }
        } else {
          Unknown(num)
        }
      }
    }
  }
}