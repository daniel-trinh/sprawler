package biz.http.client

import akka.actor.{ ActorSystem, Status, ActorRef }
import akka.spray.{ UnregisteredActorRef, RefUtils }

import biz.config.SprayCanConfig
import biz.CrawlerExceptions.{ UrlNotAllowedException, FailedHttpRequestException }
import biz.concurrency.FutureImplicits._
import biz.http.client._

import crawlercommons.robots.BaseRobotRules

import scala.async.Async._
import scala.concurrent.{ Promise, Future }
import scala.util.{ Failure, Success, Try }

import spray.http.{ StatusCodes, HttpRequest, HttpResponse }
import spray.client.pipelining._

import play.api.libs.concurrent.Execution.Implicits._
import play.libs.Akka

trait HttpClientPipelines extends Throttler {

  private implicit val system = Akka.system

  /**
   * Base url of the website being crawled.
   * {{{
   *   https://www.github.com/some/page (full url)
   *   => www.github.com (domain)
   * }}}
   */
  def domain: String

  val robotRules: Future[BaseRobotRules]

  /**
   * [[spray.can.client]] pipeline. Will only return the body of the response. Can throw a
   * [[biz.CrawlerExceptions.FailedHttpRequestException]] via parseBody.
   */
  val bodyOnlyPipeline = {
    throttledSendReceive ~> parseBody
  }

  /**
   * [[spray.can.client]] pipeline. Intended to only be used for getting "robots.txt" files.
   * Does not go through throttledSendReceive, since throttledSendReceive depends on figuring out the
   * crawl delay from robots.txt (would result in a deadlock).
   * Does not use parseBody, since parseBody accepts a Try[HttpResponse] instead of a HttpResponse, and
   * sendReceive
   */
  val fetchRobotRules = {
    sendReceiver ~>
      parseRobotRules
  }

  /**
   * Retrieve just the body of an [[spray.http.HttpResponse]].
   * @param response The future [[spray.http.HttpResponse]] to retrieve the body from.
   * @throws FailedHttpRequestException This is thrown if the HttpResponse is not a 1xx, 2xx, or 3xx status response.
   * @return future'd parsed response body
   */
  def parseBody(response: Future[HttpResponse]): Future[String] = {
    async {
      val res = await(response)
      if (res.status.isSuccess) {
        res.entity.asString
      } else {
        await(Future.failed[String](FailedHttpRequestException(res.status.intValue, res.status.reason, res.status.defaultMessage)))
      }
    }
  }

  /**
   * Intended to be a throttled drop in replacement for [[spray.client]].sendReceive
   * @return A function that receives a [[spray.http.HttpRequest]] and returns a future'd try'd [[spray.http.HttpResponse]].
   */
  def throttledSendReceive: HttpRequest => Future[HttpResponse] = {
    request =>
      {
        async {
          // Send a dummy object to throttler, and wait for an "ok" back from the throttler
          // to perform an action

          val rules = await(robotRules)
          val url = request.uri.toString()

          // Check to make sure doing a request on this URL is allowed (based on the domain's robot rules)
          await(checkAndThrottleRequest(rules.isAllowed(url), request))
        }
      }
  }

  /**
   * Performs an HTTP request for the robots.txt file of siteDomain
   * @param robotsTxtBody The contents of domain's robots.txt file
   * @return A future [[crawlercommons.robots.BaseRobotRules]]
   */
  def parseRobotRules(robotsTxtBody: Future[HttpResponse]): Future[BaseRobotRules] = {
    async {
      val response = await(robotsTxtBody)
      RobotRules.create(domain, SprayCanConfig.Client.userAgent, response.entity.asString)
    }
  }

  /**
   * Same as [[spray.client]].sendReceive
   * @return A function for performing HTTP requests.
   */
  def sendReceiver: HttpRequest => Future[HttpResponse] = {
    sendReceive
  }

  /**
   * Checks if a HttpRequest can be crawled, and queues the request to be crawled in a throttler.
   * @param urlIsAllowed True if the url can be crawled, false otherwise
   * @param request The [[spray.http.HttpRequest]] to check for crawlability
   * @return Future'd [[spray.http.HttpResponse]].
   *         The Future contains a [[biz.CrawlerExceptions.UrlNotAllowedException]] if the url is
   *         not crawlable.
   */
  private def checkAndThrottleRequest(urlIsAllowed: Boolean, request: HttpRequest): Future[HttpResponse] = {
    async {
      if (urlIsAllowed) {
        val p = Promise[Boolean]()
        await(throttler) ! PromiseRequest(p)
        await(p.future)
        play.Logger.debug(s"crawl delay rate: ${crawlDelayRate}")
        val result = await(sendReceiver(request))
        result
      } else {

        await(Future.failed[HttpResponse](
          UrlNotAllowedException(
            host = domain,
            path = request.uri.path.toString(),
            message = UrlNotAllowedException.RobotRuleDisallowed)
        ))
      }
    }
  }
}