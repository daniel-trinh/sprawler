package biz.HttpStatus.ResponseCategories

// 2xx, green
// 3xx, yellow-green? if redirect works
// 3xx, yellow if redirect fails, red if infinite redirect
// 4xx, red
// 5xx, orange? server is down, might be up later?

trait Status {
  def color: String
  def statusCode: Int
}

trait Success extends Status {
  val color = "green"
}

// can result in a timeout, or another
trait Redirect extends Status {
  val color = "grey"
}

trait ClientError extends Status {
  val color = "red"
}

trait ServerError extends Status {
  val color = "orange"
}

trait Informational extends Status {
  val color = "grey"
}
