package biz.http.client

import biz.concurrency.FutureImplicits._
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
    res: Future[HttpResponse],
    maxRedirects: Int = 5): Future[HttpResponse] = {

    // Find the Location header if one exists
    def locationHeader(redirectResponse: HttpResponse): Option[HttpHeader] = {
      redirectResponse.headers.find { header =>
        header.lowercaseName == "location"
      }
    }

    // not tail recursive, but shouldn't be a problem because maxRedirect
    // should be a low number.
    def followRedirects1(
      redirectUrl: CrawlerUrl,
      redirectResponse: Future[HttpResponse],
      redirectsLeft: Int): Future[HttpResponse] = {

      //TODO: is there a better way of coding this without having a ridiculous amount of nesting?
      async {
        play.Logger.debug(s"#1 Time: ${DateTime.now}. Redirects left: $redirectsLeft")
        val response = await(redirectResponse)
        play.Logger.debug(s"#2 Time: ${DateTime.now}. Redirects left: $redirectsLeft")

        // Only continue trying to follow redirects if status code is 3xx
        val code = response.status.intValue
        if (code < 300 || code > 400) {
          await(redirectResponse)
        } else if (redirectsLeft <= 0) {
          await(Future.failed[HttpResponse](RedirectLimitReachedException(resUrl.fromUri.toString(), resUrl.uri.toString())))
        } else {
          // Find the Location header if one exists
          val maybeLocationHeader = response.headers.find { header =>
            header.lowercaseName == "location"
          }
          maybeLocationHeader match {
            case Some(header) => {
              val newUrl = header.value

              val nextRedirectUrl: CrawlerUrl = {
                if (newUrl.startsWith("http") || newUrl.startsWith("https")) {
                  AbsoluteUrl(redirectUrl.uri, Get(newUrl).uri)
                } else {
                  val absoluteUrl = s"${redirectUrl.uri.scheme}${redirectUrl.uri.authority}$newUrl"
                  AbsoluteUrl(redirectUrl.uri, Get(absoluteUrl).uri)
                }
              }

              // val followedRedirect: Future[HttpResponse] =

              val followedRedirect: Future[HttpResponse] = async {
                val crawlerDomain = await(originCrawlerUrl.domain.asFuture)
                val nextRelativePath = nextRedirectUrl.uri.path.toString()
                val httpClient = await(CrawlerAgents.retrieveClient(nextRedirectUrl.uri))

                httpClient.get(nextRelativePath)

                val nextRedirectResponse = httpClient.get(nextRelativePath)

                val redirected = await(followRedirects1(nextRedirectUrl, nextRedirectResponse, redirectsLeft - 1))
                redirected
              }

              await(followedRedirect)
            }
            case None => {
              await(Future.failed[HttpResponse](MissingRedirectUrlException(redirectUrl.fromUri.toString(), "No URL found in redirect")))
            }
          }
        }
      }
    }

    async {
      val response = await(res)
      val code = response.status.intValue
      await(followRedirects1(resUrl, res, maxRedirects))
    }
  }
}