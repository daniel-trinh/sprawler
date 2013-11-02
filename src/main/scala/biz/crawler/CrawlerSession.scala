package biz.crawler

import java.util.concurrent.ConcurrentHashMap

/**
 * Stores state scoped to a single crawler request.
 */
class CrawlerSession {

  /**
   * Stores all visited urls across this Crawler session's lifetime. This is
   * not an immutable hash, it's reference intended to be shared.
   */
  val visitedUrls = new ConcurrentHashMap[String, Boolean]()

}