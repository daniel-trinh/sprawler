package biz.crawler

import biz.config.CrawlerConfig
import biz.CrawlerExceptions.UnprocessableUrlException
import biz.CrawlerExceptions.JsonImplicits.unprocessableUrl
import biz.http.client.HttpCrawlerClient

import play.api.libs.iteratee.Concurrent
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{ JsString, JsObject, Json, JsValue }

import akka.actor.Actor

import scala.async.Async.{ async, await }
import scala.util.{ Try, Success, Failure }
import scala.{ Some, None }

class CrawlerActor(val channel: Concurrent.Channel[JsValue]) extends Actor with Streams {
  import CrawlerAgents._

  def receive = {
    case info @ UrlInfo(fromUrl, toUrl, crawlerTPD, depth) => {
      play.Logger.info(s"Message received: $info")
      if (depth <= CrawlerConfig.maxDepth) {
        info.topPrivateDomain match {
          case Failure(err) =>
            streamJsonError(Json.toJson(UnprocessableUrlException(info.fromUrl, info.toUrl, err.getMessage)))
          case Success(topDomain) =>
            val client = getClient(topDomain, info.domain)
            async {
              val result = await(client.get(info.path))
              result match {
                case Failure(err) =>
                  streamJsonErrorFromException(err)
                case Success(response) =>
                  streamJsResponse(fromUrl, toUrl, response)
              }
              cleanup()
            }

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

  private def getClient(topPrivateDomain: String, domain: String): HttpCrawlerClient = {
    crawlerClients().get(topPrivateDomain) match {
      case Some(client) =>
        client
      case None =>
        val httpClient = HttpCrawlerClient(domain)

        crawlerClients send { s =>
          // avoid updating the hashtable if another client has already been added asynchronously
          s.getOrElseUpdate(topPrivateDomain, httpClient)
          s
        }
        httpClient
    }
  }

}

