package biz

object CustomExceptions {
  class FailedHttpRequestError(message: String) extends RuntimeException(message)
}
