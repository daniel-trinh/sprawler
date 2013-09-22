package biz.crawler

import akka.agent.Agent

import biz.http.client.HttpCrawlerClient

import play.Logger
import play.api.libs.concurrent.Execution.Implicits._

import scala.collection.mutable

import spray.http.Uri

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
  val crawlerClients = Agent(new mutable.HashMap[String, HttpCrawlerClient])

  /**
   *
   * @param uri This is the URI of the URL that you want to crawl next, and not the URI of an already
   *            crawled URL.
   * @param portOverride This is used for unit tests.
   * @return
   */
  def getClient(uri: Uri, portOverride: Option[Int] = None): HttpCrawlerClient = {
    val urlKey = s"${uri.scheme}${uri.authority.host}"

    crawlerClients().get(urlKey) match {
      case Some(client) =>
        client
      case None =>
        val httpClient = HttpCrawlerClient(uri)
        Logger.debug(s"httpClientUri:$uri")
        crawlerClients send { s =>
          // avoid updating the hashtable if another client has already been added asynchronously
          s.getOrElseUpdate(urlKey, httpClient)
          s
        }
        httpClient
    }
  }
}