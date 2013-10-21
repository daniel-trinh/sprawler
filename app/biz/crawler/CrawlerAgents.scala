package biz.crawler

import akka.agent.Agent

import biz.http.client.HttpCrawlerClient

import play.Logger
import play.api.libs.concurrent.Execution.Implicits._

import scala.collection.mutable

import spray.http.Uri

import scala.concurrent.Future
import spray.caching.{ LruCache, Cache }
import spray.util._

/**
 * Things that need to be shared across the crawler's session
 * TODO: move visited urls into Redis
 */
object CrawlerAgents {

  /**
   * Stores all visited urls across this Crawler session's lifetime.
   */
  val visitedUrls = Agent(new mutable.HashSet[String]())

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
  def retrieveClient(uri: Uri): Future[HttpCrawlerClient] = {
    crawlerClientCache(uri.authority.host) {
      Future.successful(HttpCrawlerClient(uri))
    }
  }

}