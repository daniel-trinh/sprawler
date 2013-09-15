package biz.http.client

import biz.crawler.url.{ AbsoluteUrl, CrawlerUrl }
import biz.CrawlerExceptions.{ MissingRedirectUrlException, RedirectLimitReachedException }
import biz.crawler.CrawlerAgents

import play.api.libs.concurrent.Execution.Implicits._

import spray.http._
import spray.client.pipelining._

import scala.async.Async.{ async, await }
import scala.concurrent.Future
import scala.util.{ Try, Success, Failure }

trait RedirectFollower {
  def originCrawlerUrl: CrawlerUrl

  /**
   * Follows redirects until a non 2xx response is reached or some sort of
   * error occurrs
   *
   * @param resUrl
   * @param res
   * @param maxRedirects
   * @return
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
            // Only continue trying to follow redirects if status code is 3xx
            val code = response.status.intValue
            if (code < 300 || code > 400) {
              await(redirectResponse)
            } else if (redirectsLeft <= 0) {
              Failure(RedirectLimitReachedException(resUrl.fromUri.toString(), resUrl.uri.toString()))
            } else {
              // Find the Location header if one exists
              val maybeLocationHeader = response.headers.find { header =>
                header.lowercaseName == "location"
              }
              maybeLocationHeader match {
                case Some(header) => {
                  val newUrl = header.value
                  val nextRedirectUrl: CrawlerUrl = if (newUrl.startsWith("http") || newUrl.startsWith("https")) {
                    AbsoluteUrl(redirectUrl.uri, Get(newUrl).uri)
                  } else {
                    val absoluteUrl = s"${redirectUrl.uri.scheme}${redirectUrl.uri.authority}$newUrl"
                    AbsoluteUrl(redirectUrl.uri, Get(absoluteUrl).uri)
                  }

                  val tryResponse = for {
                    crawlerDomain <- originCrawlerUrl.domain
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
          }
          case Failure(e) => {
            Failure(e)
          }
        }
      }
    }

    async {
      await(res) match {
        // Check to make sure the status is actually a 300, if not, return the provided response.
        case Success(response) =>
          val code = response.status.intValue
          await(followRedirects1(resUrl, res, maxRedirects))
        case Failure(error) => {
          Failure(error)
        }
      }
    }
  }

}
