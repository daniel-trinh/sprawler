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
import spray.http.{ HttpHeader, HttpResponse }
import spray.http.HttpHeaders.Location
import spray.client.pipelining._
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

      //TODO: is there a better way of coding this without having a ridiculous amount of nesting?
      async {
        await(redirectResponse) match {
          case Success(response) => {
            // Find the Location header if one exists
            val maybeLocationHeader = response.headers.find { header =>
              header.lowercaseName == "location"
            }
            maybeLocationHeader match {
              case Some(header) => {
                val newUrl = header.value
                val nextRedirectUrl: CrawlerUrl = if (newUrl.startsWith("https://") || newUrl.startsWith("https://")) {
                  AbsoluteUrl(redirectUrl.uri, Get(newUrl).uri)
                } else {
                  val absoluteUrl = s"${redirectUrl.uri.scheme}${redirectUrl.uri.authority.host}$newUrl"
                  AbsoluteUrl(redirectUrl.uri, Get(absoluteUrl).uri)
                }

                val tryResponse = for {
                  crawlerDomain <- crawlerUrl.domain
                  nextRelativePath = nextRedirectUrl.uri.path.toString()
                } yield {
                  val httpClient = CrawlerAgents.getClient(nextRedirectUrl.uri)
                  httpClient.get(nextRelativePath)
                }

                tryResponse match {
                  case Success(nextRedirectResponse) => {
                    await(followRedirects1(nextRedirectUrl, nextRedirectResponse, redirectsLeft - 1))
                  }
                  case Failure(x) => Failure(x)
                }
              }
              case None => {
                Failure(MissingRedirectUrlException(redirectUrl.fromUri.toString(), "No URL found in redirect"))
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
          val code = response.status.intValue
          if (code >= 300 && code < 400) {
            if (maxRedirects > 0) {
              await(followRedirects1(resUrl, res, maxRedirects))
            } else {
              Failure(RedirectLimitReachedException(resUrl.fromUri.toString(), resUrl.uri.toString()))
            }
          } else {
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
        val relativePath = info.uri.path.toString()
        val client = getClient(info.uri)
        async {
          val result = await(client.get(relativePath))
          result match {
            case Failure(err) =>
              streamJsonErrorFromException(err, eofAndEnd = false)
            case Success(response) =>
              streamJsonResponse(info.fromUri.toString(), info.uri.toString(), response)
              val code = response.status.intValue
              if (code >= 300 && code < 400) {
                followRedirects(info, Future(Success(response)))
              }
          }
          cleanup()
        }
      } else {
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

