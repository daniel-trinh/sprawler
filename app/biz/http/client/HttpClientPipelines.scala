package biz.http.client

import akka.actor.{ Status, ActorRef }
import akka.spray.{ UnregisteredActorRef, RefUtils }

import biz.config.SprayCanConfig
import biz.CrawlerExceptions.{ UrlNotAllowedException, FailedHttpRequestException }
import biz.concurrency.FutureImplicits._
import biz.http.client._

import crawlercommons.robots.BaseRobotRules

import scala.async.Async._
import scala.concurrent.{ Promise, Future }
import scala.util.{ Failure, Success, Try }

import spray.client.HttpConduit._
import spray.http.{ StatusCodes, HttpRequest, HttpResponse }

import play.api.libs.concurrent.Execution.Implicits._
import scala.annotation.tailrec
import spray.http.HttpHeaders.Location

trait HttpClientPipelines extends Throttler {

  /**
   * [[spray.can.client.HttpClient]] conduit actor. It is left abstract to allow for the user
   * to specify their own conduit as needed. This is a boilerplate requirement in order to perform HTTP requests
   * using spray.client.
   */
  def conduit: ActorRef

  /**
   * Base url of the website being crawled.
   * {{{
   *   https://www.github.com/some/page (full url)
   *   => www.github.com (domain)
   * }}}
   */
  def domain: String

  def robotRules: Future[Try[BaseRobotRules]]

  /**
   * [[spray.can.client.HttpClient]] pipeline. Will only return the body of the response. Can throw a
   * [[biz.CrawlerExceptions.FailedHttpRequestException]] via parseBody.
   */
  lazy val bodyOnlyPipeline = {
    throttledSendReceive ~> parseBody
  }

  /**
   * [[spray.can.client.HttpClient]] pipeline. Intended to only be used for getting "robots.txt" files.
   * Does not go through throttledSendReceive, since throttledSendReceive depends on figuring out the
   * crawl delay from robots.txt (would result in a deadlock).
   * Does not use parseBody, since parseBody accepts a Try[HttpResponse] instead of a HttpResponse, and
   * sendReceive
   */
  lazy val fetchRobotRules = {
    sendReceiveTry(conduit) ~>
      parseRobotRules
  }

  /**
   * Retrieve just the body of an [[spray.http.HttpResponse]].
   * @param response The future [[spray.http.HttpResponse]] to retrieve the body from.
   * @throws FailedHttpRequestException This is thrown if the HttpResponse is not a 1xx, 2xx, or 3xx status response.
   * @return future'd parsed response body
   */
  def parseBody(response: Future[Try[HttpResponse]]): Future[Try[String]] = {
    response map { res =>
      res map { r =>
        if (r.status.isSuccess) {
          r.entity.asString
        } else
          throw new FailedHttpRequestException(r.status.value, r.status.reason, r.status.defaultMessage)
      }
    }
  }

  /**
   * Intended to be a throttled drop in replacement for [[spray.client.HttpConduit]].sendReceive
   * @return A function that receives a [[spray.http.HttpRequest]] and returns a future'd try'd [[spray.http.HttpResponse]].
   */
  def throttledSendReceive: HttpRequest => Future[Try[HttpResponse]] = {
    request =>
      {
        async {
          // Send a dummy object to throttler, and wait for an "ok" back from the throttler
          // to perform an action
          val tryRules = await(robotRules)
          val url = s"${domain}${request.uri}"

          // Check to make sure doing a request on this URL is allowed (based on the domain's robot rules)
          val asdf = tryRules match {
            case Success(rules) =>
              checkUrl(rules.isAllowed(url), request)
            case Failure(throwable) =>
              // TODO: is there a better way of doing this? looks like unnecessary boxing
              Future(Failure(throwable))
          }
          await(asdf)
        }
      }
  }

  /**
   * Performs an HTTP request for the robots.txt file of siteDomain
   * @param robotsTxtBody The contents of domain's robots.txt file
   * @return A future [[crawlercommons.robots.BaseRobotRules]]
   */
  def parseRobotRules(robotsTxtBody: Future[Try[HttpResponse]]): Future[Try[BaseRobotRules]] = {
    async {
      await(robotsTxtBody) map { response =>
        RobotRules.create(domain, SprayCanConfig.Client.userAgent, response.entity.asString)
      }
    }
  }

  def sendReceiveTry(httpConduitRef: ActorRef): HttpRequest => Future[Try[HttpResponse]] = {
    request =>
      {
        val futureResponse = sendReceive(httpConduitRef)(request)
        futureResponse.tryMe
      }
  }

  private def customSendReceive(httpConduitRef: ActorRef): HttpRequest => Future[HttpResponse] = {
    require(RefUtils.isLocal(httpConduitRef), "sendReceive cannot be constructed for remote HttpConduits")
    request => {
      val promise = Promise[HttpResponse]()
      val receiver = new UnregisteredActorRef(httpConduitRef) {
        def handle(message: Any)(implicit sender: ActorRef) {
          message match {
            case x: HttpResponse       => promise.success(x)
            case Status.Failure(error) => promise.failure(error)
          }
        }
      }
      httpConduitRef.tell(request, receiver)
      promise.future
    }
  }

  private def checkUrl(b: Boolean, request: HttpRequest): Future[Try[HttpResponse]] = {
    async {
      if (b) {
        val p = Promise[Boolean]()
        await(throttler) ! PromiseRequest(p)
        await(p.future)

        await(sendReceiveTry(conduit)(request))
      } else {
        Failure(
          UrlNotAllowedException(
            host = domain,
            path = request.uri,
            message = UrlNotAllowedException.RobotRuleDisallowed)
        )
      }
    }
  }
}
