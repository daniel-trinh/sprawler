package biz.httpStatus

// for each link
// store unfound links for crawling later

// for each link in store
// crawl link, check response code, add timeout
// return result to user somehow

// store total links crawled / links left to find?

// store links in an actor

// add delay between crawing links?

// actor for visiting links?
// future list for visiting links?

// given a base url, print each link that is crawled 
// along with its response code

// what about links that aren't an html/xml structure? ignore?

// crawling links in parallel? how to add a delay 

import biz.httpStatus.ResponseCategories

object SpecificResponses {

  case object Ok extends Success2xx {
    val statusCode = 200
  }

  case object NoContent extends Success2xx {
    val statusCode = 204
  }

  case object NotFound extends ClientError4xx {
    val statusCode = 404
  }

  case object UnprocessableEntity extends ClientError4xx {
    val statusCode = 422
  }

  case object BadGateway extends ServerError5xx {
    val statusCode = 503
  }

  case object InternalError extends ServerError5xx {
    val statusCode = 500
  }

  case class Informational(statusCode: Int) extends Informational1xx
  case class Success(statusCode: Int) extends Success2xx
  case class Redirect(statusCode: Int) extends Redirect3xx
  case class ClientError(statusCode: Int) extends ClientError4xx
  case class ServerError(statusCode: Int) extends ServerError5xx
  case class Unknown(statusCode: Int) extends UnknownStatus

}