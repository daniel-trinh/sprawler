package biz.HttpStatus.SpecificResponses

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

case object Ok extends Success {
  val statusCode = 200
}

case object NoContent extends Success {
  val statusCode = 204
}

case object NotFound extends ClientError {
  val statusCode = 404
}

case object UnprocessableEntity extends ClientError {
  val statusCode = 422
}

case object BadGateway extends ServerError {
  val statusCode = 503
}

case object InternalError extends ServerError {
  val statusCode = 500
}

case class Informational(statusCode: Int) extends Informational
case class Success(statusCode: Int) extends Success
case class Redirect(statusCode: Int) extends Redirect
case class ClientError(statusCode: Int) extends ClientError
case class ServerError(statusCode: Int) extends ServerError

case class Unknown(statusCode: Int) extends Status {
  val color = "purple"
}

// 2xx, green
// 3xx, yellow-green? if redirect works
// 3xx, yellow if redirect fails, red if infinite redirect
// 4xx, red
// 5xx, orange? server is down, might be up later?
