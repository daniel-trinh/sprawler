package biz.httpStatus

import biz.httpStatus.specificResponses

// Object for storing information about an Http Response.
case class HttpResponse(statusCode: Int, body: String, headers: String) {

  // Returns a categorized status w/ information
  // about the type
  def status: Status = {
    statusCode match {
      case 200 => Ok
      case 201 => Created
      case 202 => Accepted
      case 204 => NoContent
      case 301 => MovedPermanently
      case 302 => Found
      case 308 => PermanentRedirect
      case 400 => BadRequest
      case 401 => Unauthorized
      case 402 => PaymentRequired
      case 403 => Forbidden
      case 406 => NotAcceptable
      case 404 => NotFound
      case 411 => LengthRequired
      case 500 => InternalServerError
      case 503 => BadGateway
      case num: Int => {
        if (num % 100 < 100) {
          Informational(num)
        } else if (num % 200 < 100) {
          Success(num)
        } else if (num % 300 < 100) {
          Redirect(num)
        } else if (num % 400 < 100) {
          ClientError(num)
        } else if num( % 500 < 100) {
          ServerError(num)
        }
      }
      case num: Int => {}
      case _ => // log error
    }
  }
}