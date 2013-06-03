package biz.crawler

import biz.config.CrawlerConfig
import biz.CrawlerExceptions.{ RedirectLimitReachedException, MissingRedirectUrlException, UnprocessableUrlException }

import play.api.libs.iteratee.Concurrent
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{ JsString, JsObject, Json, JsValue }

import akka.actor.Actor

import scala.async.Async.{ async, await }
import scala.util.{ Try, Success, Failure }
import scala.{ Some, None }
import spray.http.HttpResponse
import spray.http.HttpHeaders.Location
import scala.concurrent.Future
import scala.annotation.tailrec

trait RedirectFollower {
  def crawlerUrl: CrawlerUrl
  /**
   * Follows redirects until a non 2xx response is reached or some sort of
   * error occurrs
   */
  def followRedirects(
    resUrl: CrawlerUrl,
    res: Future[Try[HttpResponse]],
    maxRedirects: Int = 5): Future[Try[HttpResponse]] = {

    // not tail recursive, but shouldn't be a problem because maxRedirect should be a low number.
    def followRedirects1(
      redirectUrl: CrawlerUrl,
      redirectResponse: Future[Try[HttpResponse]],
      redirectsLeft: Int): Future[Try[HttpResponse]] = {

      async {
        await(redirectResponse) match {
          case Success(response) => {
            response.header[Location] match {
              case Some(Location(newUrl)) => {
                val nextRedirectUrl: CrawlerUrl = if (newUrl.startsWith("https://")) {
                  AbsoluteHttpsUrl(redirectUrl.url, newUrl)
                }
                else if (newUrl.startsWith("http://")) {
                  AbsoluteHttpUrl(redirectUrl.url, newUrl)
                }
                else {
                  RelativeUrl(redirectUrl.url, newUrl, crawlerUrl.domain)
                }
                nextRedirectUrl.topPrivateDomain match {
                  case Success(tpd) =>
                    val httpClient = CrawlerAgents.getClient(tpd, crawlerUrl.domain)
                    val nextRedirectResponse = httpClient.get(nextRedirectUrl.relativePath)
                    await(followRedirects1(nextRedirectUrl, nextRedirectResponse, redirectsLeft - 1))
                  case Failure(error) =>
                    Failure(error)
                }
              }
              case None => {
                Failure((MissingRedirectUrlException(redirectUrl.fromUrl, "No URL found in redirect")))
              }
            }
          }
          case Failure(e) => {
            Failure(e)
          }
        }
      }
    }

    async {
      await(res) match {
        case Success(response) =>
          val code = response.status.value
          if (code >= 300 && code < 400) {
            if (maxRedirects > 0) {
              await(followRedirects1(resUrl, res, maxRedirects))
            }
            else {
              Failure(RedirectLimitReachedException(resUrl.fromUrl, resUrl.url))
            }
          }
          else {
            await(res)
          }
        case Failure(error) => {
          Failure(error)
        }
      }
    }
  }

}
class CrawlerActor(val channel: Concurrent.Channel[JsValue], val crawlerUrl: CrawlerUrl)
    extends Actor with Streams with RedirectFollower {
  import CrawlerAgents._

  def receive = {
    case info: CrawlerUrl => {
      play.Logger.info(s"Message received: $info")
      if (info.depth <= CrawlerConfig.maxDepth) {
        info.topPrivateDomain match {
          case Failure(err) =>
            streamJsonErrorFromException(UnprocessableUrlException(info.fromUrl, info.url, err.getMessage))
          case Success(topDomain) =>
            val client = getClient(topDomain, info.domain)
            async {
              val result = await(client.get(info.relativePath))
              result match {
                case Failure(err) =>
                  streamJsonErrorFromException(err, eofAndEnd = false)
                case Success(response) =>
                  streamJsonResponse(info.fromUrl, info.url, response)
                  val code = response.status.value
                  if (code >= 300 && code < 400) {
                    followRedirects(info, Future(Success(response)))
                  }
              }
              cleanup()
            }
        }
      }
      else {
        channel.push(JsObject(
          Seq("complete" -> JsString(s"Maximum crawl depth has been reached. Depth: ${CrawlerConfig.maxDepth}"))
        ))
      }
    }
    case m @ _ => {
      play.Logger.info(s"Message received")
      play.Logger.error(s"Unexpected message: $m")
    }
  }

}

