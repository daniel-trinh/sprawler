package biz

/**
 * Exceptions used for this webcrawler project
 */
object CustomExceptions {

  case class FailedHttpRequestError(message: String) extends RuntimeException(message)

  /**
   * Throw this if a URL cannot be crawled due to restrictions in robots.txt
   * @param host
   * @param path
   * @param message
   */
  case class UrlNotAllowed(host: String, path: String, message: String) extends RuntimeException(message)

  object UrlNotAllowed {
    val RobotRuleDisallowed = "This domain's robot rules does not allow crawling of this url."
  }
}
