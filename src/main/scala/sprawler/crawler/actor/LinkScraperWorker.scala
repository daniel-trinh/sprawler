
package sprawler.crawler.actor

import sprawler.crawler.url.CrawlerUrl

import scala.reflect._

import akka.actor.ActorRef

import spray.http.HttpResponse

import scala.util.Try
import scala.concurrent.Future

/**
 * Crawls, finds links, sends them to [[sprawler.crawler.actor.LinkScraperWorker.master]]
 * to queue, and waits for more links to crawl.
 *
 * @param master Reference to the master Actor.
 *               The master actor is intended to be [[sprawler.crawler.actor.LinkQueueMaster]].
 * @param originCrawlerUrl The original URL that started this crawling session. Since this URL
 *                         is the very first URL, [[sprawler.crawler.url.CrawlerUrl.fromUri]] will
 *                         be the same as [[sprawler.crawler.url.CrawlerUrl.uri]].
 */
class LinkScraperWorker(
  val master: ActorRef,
  originCrawlerUrl: CrawlerUrl)
    extends Worker[CrawlerUrl] with LinkScraper {

  //  override implicit def t = classTag[CrawlerUrl]

  implicit val system = context.system

  /**
   * Attempts to crawl the provided url.
   *
   * After a url is crawled, depending on the [[spray.http.HttpResponse]].status.intValue,
   * different scenarios can occur:
   *
   * 200 - Extracts links from body, checks if the urls are crawlable,
   *       and sends the crawlable ones to the master actor.
   * 3xx - Extracts link from the 'Location' HTTP header, and sends it to
   *       the master actor for crawling, with a special 'redirectsLeft' flag.
   *
   * @param url The url to crawl. Specifically, [[sprawler.crawler.url.CrawlerUrl.uri]] is used as
   *        the target url for crawling.
   *
   * @return A Future'd HttpResponse from crawling url, which is passed to the
   *         onUrlComplete function.
   */
  def doWork(url: CrawlerUrl): Future[HttpResponse] = {
    crawlUrl(url)
  }
}