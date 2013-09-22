package biz.crawler.actor

import akka.actor.PoisonPill
import biz.concurrency.FutureActions._

import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits._

import spray.can.client.{ HostConnectorSettings, ClientConnectionSettings }
import scala.concurrent.duration._

class LinkQueueMaster[CrawlerUrl] extends Master {
  private var urlsLeftToCrawl = 0
  // private val httpRequestTimeout = ClientConnectionSettings(Akka.system).requestTimeout.length
  // private val requestRetryLimit = HostConnectorSettings(Akka.system).maxRetries

  override def workHook() {
    urlsLeftToCrawl += 1
  }

  override def workItemDoneHook() {
    urlsLeftToCrawl -= 1

    // We can't simply check to see if the queue is empty, because a link crawling worker actor might
    // still be processing a url -- we wait for the worker to tell us that it has finished
    // crawling a URL before considering a URL done.
    if (urlsLeftToCrawl == 0) {
      self ! PoisonPill
    }
  }
}