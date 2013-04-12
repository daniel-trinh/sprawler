package biz

import akka.agent._
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.{ Promise, Future }
import scala.concurrent.duration._
import scala.collection.mutable

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.concurrent.Akka
import play.api.Play.current

import crawlercommons.robots
import com.google.common.net.InternetDomainName
import spray.util.SprayActorLogging

// Need a way to time out / stop crawling if
// there seems to be some sort of infinite loop...
// tagged urls with depth? Max depth?
case class Crawler(domain: String) {

  private val visitedUrls = Agent(new mutable.HashSet[String]())(Akka.system)
  val topPrivateDomain = InternetDomainName.fromLenient(domain).topPrivateDomain().name()

  def crawl(url: String) {

  }
}

case class Url(url: String, baseUrlTPD: String, depth: Int = 0)(implicit visitedUrls: Agent[mutable.HashSet[String]]) {
  val isTopPrivateDomain: Boolean = {
    val domainName = InternetDomainName.fromLenient(url)
    if (domainName.topPrivateDomain().name() == baseUrlTPD)
      true
    else
      false
  }

  // May report false negatives because of Agent behavior
  val isVisited: Boolean = visitedUrls().contains(url)

  val isWithinDepth: Boolean = {
    depth <= config.Crawler.maxDepth
  }

  lazy val isCrawlable: Boolean = !isVisited && isTopPrivateDomain
}

// create new actor to find new links
// create new actor to crawl links from queue

// Should this stuff be rewritten as iteratees and enumerators?
class UrlFinder extends Actor with SprayActorLogging {
  def receive = {
    case url @ Url(link, domain, depth) => {
      // TODO: move this depth 1000 into a config file/param
      if (depth <= config.Crawler.maxDepth)
        ???
      else
        ???
    }
    //    case res @ HttpResponse(status, body, headers) => {
    //
    //    }
    case m @ _ => log.error(s"Unexpected message: $m")
  }

  //  def linkIterator(url: Url): Iterator[Url] = {
  //
  //  }
}