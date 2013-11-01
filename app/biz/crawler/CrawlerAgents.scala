package biz.crawler

import akka.actor.ActorSystem

import biz.http.client.HttpCrawlerClient

import scala.concurrent.{ Future, ExecutionContext }

import spray.http.Uri
import spray.caching.{ LruCache, Cache }

/**
 * Things that need to be shared across the crawler's session
 */
object CrawlerAgents {

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
  def retrieveClient(uri: Uri)(implicit ec: ExecutionContext, system: ActorSystem): Future[HttpCrawlerClient] = {
    crawlerClientCache(uri.authority.host) {
      Future.successful(HttpCrawlerClient(uri)(system))
    }
  }
}