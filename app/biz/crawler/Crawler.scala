package biz.Crawler

import akka.agent._
import akka.actor._
import libs.concurrent.Akka
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.{ Promise, Future }
import concurrent.duration._

// Need a way to time out / stop crawling if 
// there seems to be some sort of infinite loop...
// tagged urls with depth? Max depth?
case class Crawler(baseUrl: String) {

  val urlsToVisit: Agent[mutable.Queue]()
  val visitedUrls: Agent[mutable.HashSet]()

  def crawl(url: String) {

  }
}

case class Url(url: String, baseUrl: String, depth: Int = 0) {

  // How to detect dynamically generated urls?
  lazy val isVisited: Boolean = visitUrls.get(url) match {
    case Some(url) => true
    case None      => false
  }

  lazy val isCrawlable: Boolean = !isVisited && isSameBaseUrl 

  lazy val isSameBaseUrl: Boolean =
    if url.indexOf?(baseUrl) == 0 {
      true
    } else {
      false
    }
}

// create new actor to find new links
// create new actor to crawl links from queue

// Should this stuff be rewritten as iteratees and enumerators?
class UrlFinder extends Actor {
  def receive = {
    case url @ Url(link, domain, depth) => {
      // TODO: move this depth 1000 into a config file/param
      if (depth <= 1000)
    }
    case res @ HttpResponse(status, body, headers) => {
    }
    case m @ _: => log.error(s"Unexpected message: $m")
  }

  def linkIterator(url: Url): Iterator[Url] = {

  }
}

class UrlCrawler extends Actor {
  def receive = {
    case url @ Url(link, domain, depth) => {
      if url.isCrawlable
    }
    case m @ _: => log.error(s"Unexpected message: $m")
  }
}