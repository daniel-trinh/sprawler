package biz

import akka.agent._
import akka.actor._

import scala.concurrent.{ Promise, Future }
import scala.collection.mutable
import scala.async.Async.{ async, await }
import scala.util.{ Try, Success, Failure }
import scala.{ Some, None }

import play.api.libs.json.{ JsObject, JsString, JsValue }
import play.api.libs.iteratee._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.concurrent.Akka
import play.api.Play.current

import crawlercommons.robots
import com.google.common.net.InternetDomainName

import java.net.URI

/**
 * Entry point of Crawler.
 * @param url The initial URL to start crawling from.
 */
case class Crawler(url: String) extends UrlHelper {

  private val uri = new URI(url)
  lazy val domain = uri.getHost
  lazy val crawlerUrlInfo = UrlInfo(url, calculateTPD(domain))(CrawlerAgents.visitedUrls)

  // TODO: this method will start the crawling process
  def crawl: Future[(Iteratee[JsValue, _], Enumerator[JsValue])] = {
    val enumerator = Concurrent.unicast[JsValue] { c =>
      play.Logger.info(s"Crawler started: $url")
      val crawlerActor = Akka.system.actorOf(Props(new CrawlerActor(c)))

      crawlerActor ! crawlerUrlInfo
    }

    val iteratee = Iteratee.foreach[JsValue] { event =>
      play.Logger.info(s"received: $event")
    }.mapDone { event =>
      play.Logger.info(s"end: $event")
    }

    Future(iteratee, enumerator)
  }
}

case class UrlInfo(url: String, crawlerTopPrivateDomain: String, depth: Int = 0)(implicit visitedUrls: Agent[mutable.HashSet[String]]) extends UrlHelper {
  // May report false negatives because of Agent behavior
  val isVisited: Boolean = visitedUrls().contains(url)
  lazy val domain: String = calculateDomain(url)
  val topPrivateDomain: String = calculateTopPrivateDomain(domain)

  /**
   * Tests if the provided url's UrlHelper matches the base crawler's UrlHelper
   * {{{
   *  val url = UrlInfo("https://www.github.com/some/path", "github.com")
   *  url.sameTPD
   *  => true
   *
   *  val url = UrlInfo("https://www.github.com/some/path", "google.com")
   *  url.sameTPD
   *  => false
   * }}}
   */
  val sameTPD: Boolean = {
    if (topPrivateDomain == crawlerTopPrivateDomain)
      true
    else
      false
  }

  val isWithinDepth: Boolean = {
    depth <= config.Crawler.maxDepth
  }

  val isCrawlable: Boolean = !isVisited && sameTPD

  private def calculateTopPrivateDomain(domain: String): String = {
    InternetDomainName.fromLenient(domain).topPrivateDomain().name()
  }
}

class CrawlerActor(val channel: Concurrent.Channel[JsValue]) extends Actor with WebsocketHelper {
  import CrawlerAgents._

  def receive = {
    case info @ UrlInfo(url, crawlerTPD, depth) => {
      if (depth <= config.Crawler.maxDepth) {
        Try(info.topPrivateDomain) match {
          case Failure(err) => streamError(s"There was a problem trying to parse the url: ${err.getMessage}")
          case Success(topDomain) => {
            val client: HttpCrawlerClient = getClient(info)
            client.robotRules
          }
        }
      } else {
        channel.push(JsObject(
          Seq("complete" -> JsString(s"Maximum crawl depth has been reached. Depth: ${config.Crawler.maxDepth}"))
        ))
        channel.eofAndEnd()
        closeAgents()
      }
    }
    case m @ _ => play.Logger.error(s"Unexpected message: $m")
  }

  private def getClient(urlInfo: UrlInfo): HttpCrawlerClient = crawlerClients().get(urlInfo.topPrivateDomain) match {
    case Some(cc) => cc
    case None => {
      val httpClient = HttpCrawlerClient(urlInfo.domain)

      crawlerClients send { s: mutable.HashMap[String, HttpCrawlerClient] =>
        s += ((urlInfo.topPrivateDomain, httpClient))
        s
      }

      httpClient
    }
  }
}

trait UrlHelper {
  def calculateTPD(domain: String): String = {
    InternetDomainName.fromLenient(domain).topPrivateDomain().name()
  }

  def calculateDomain(url: String): String = {
    new URI(url).getHost
  }
}

trait WebsocketHelper {

  // For pushing crawl updates to the client
  val channel: Concurrent.Channel[JsValue]

  def streamError(errorMsg: String): Future[(Iteratee[JsValue, _], Enumerator[JsValue])] = {
    // A finished Iteratee sending EOF
    val iteratee = Done[JsValue, Unit]((), Input.EOF)

    // Send an error and close the socket
    val enumerator = Enumerator[JsValue](
      JsObject(
        Seq("error" -> JsString(errorMsg))
      )
    ).andThen(Enumerator.enumInput(Input.EOF))

    Future((iteratee, enumerator))
  }
}

/**
 * Things that need to be shared across the crawler's session
 */
object CrawlerAgents {

  /**
   * Stores all visited urls across this Crawler session's lifetime.
   */
  val visitedUrls = Agent(new mutable.HashSet[String]())(Akka.system)

  /**
   * Lookup table for robots.txt rules for a particular url
   */
  val crawlerClients = Agent(new mutable.HashMap[String, HttpCrawlerClient])(Akka.system)

  def closeAgents() {
    visitedUrls.close()
    crawlerClients.close()
  }
}

case class CrawlerSocket[A](e: Enumerator[A])