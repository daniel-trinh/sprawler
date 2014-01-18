package sprawler.crawler

import akka.actor.ActorSystem

import sprawler.config._
import sprawler.http.client.HttpCrawlerClient

import java.util.concurrent.ConcurrentHashMap

import spray.caching.{ LruCache, Cache }
import spray.http.Uri

import scala.concurrent.{ Future, ExecutionContext }
import sprawler.CrawlerExceptions.UnknownException

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

/**
 * Things that need to be shared across all crawler sessions
 */
object CrawlerSession {

  /**
   * Lookup table for robots.txt rules for a particular url
   */
  val crawlerClientCache: Cache[HttpCrawlerClient] = LruCache()

  /**
   *
   * @param uri This is the URI of the URL that you want to crawl next, and not the URI of an already
   *            crawled URL.
   * @return
   */
  def retrieveClient(
    uri:           Uri,
    crawlerConfig: CrawlerConfig = DefaultCrawlerConfig)(
      implicit
      ec:     ExecutionContext,
      system: ActorSystem
    ): Future[HttpCrawlerClient] = {
    if (uri.authority.isEmpty) {
      Future.failed(UnknownException(s"uri with empty authority passed: uri: $uri"))
    } else {
      crawlerClientCache(uri.authority.host) {
        Future.successful(HttpCrawlerClient(uri, crawlerConfig)(system))
      }
    }
  }
}