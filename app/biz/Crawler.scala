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
import biz.config.CrawlerConfig

// url not crawlable due to robots
// timeout
// 1xx, 2xx, 3xx, 4xx, 5xx

/**
 * Entry point of Crawler.
 * @param url The initial URL to start crawling from.
 */
case class Crawler(url: String) extends UrlHelper {

  private val uri = new URI(url)
  lazy val domain = uri.getHost

  // TODO: this method will start the crawling process
  def crawl: Future[Enumerator[JsValue]] = {
    val enumerator = Concurrent.unicast[JsValue] { c =>
      play.Logger.info(s"Crawler started: $url")
      val crawlerActor = Akka.system.actorOf(Props(new CrawlerActor(c)))
      // Info of initial URL to start crawling
      lazy val crawlerUrlInfo = UrlInfo(url, calculateTPD(domain))(CrawlerAgents.visitedUrls)
      crawlerActor ! crawlerUrlInfo
    }

    Future(enumerator)
  }
}

class CrawlerActor(val channel: Concurrent.Channel[JsValue]) extends Actor with WebsocketHelper {
  import CrawlerAgents._

  def receive = {
    case info @ UrlInfo(url, crawlerTPD, depth) => {
      if (depth <= config.CrawlerConfig.maxDepth) {
        Try(info.topPrivateDomain) match {
          case Failure(err) => streamError(s"""There was a problem trying to parse the url "${info.url}": ${err.getMessage}""")
          case Success(topDomain) => {
            channel.push(
              JsObject(
                Seq("test" -> JsString("123"))
              )
            )
          }
        }
      } else {
        channel.push(JsObject(
          Seq("complete" -> JsString(s"Maximum crawl depth has been reached. Depth: ${config.CrawlerConfig.maxDepth}"))
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

      crawlerClients send { s =>
        val tpd = urlInfo.topPrivateDomain
        // avoid updating the hashtable if another client has already been added asynchronously
        s.getOrElseUpdate(tpd, httpClient)
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

  /**
   * Closes agents -- necessary to prevent memory leaks
   */
  def closeAgents() {
    visitedUrls.close()
    crawlerClients.close()
  }
}

case class Start(url: String)
case class UrlInfo(url: String, crawlerTopPrivateDomain: String, depth: Int = CrawlerConfig.maxDepth)(implicit visitedUrls: Agent[mutable.HashSet[String]]) extends UrlHelper {
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
    depth <= config.CrawlerConfig.maxDepth
  }

  val isCrawlable: Boolean = !isVisited && sameTPD

  private def calculateTopPrivateDomain(domain: String): String = {
    InternetDomainName.fromLenient(domain).topPrivateDomain().name()
  }
}
