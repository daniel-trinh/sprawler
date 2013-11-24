package sprawler.crawler.actor

import akka.actor.{ Props, PoisonPill }
import akka.routing.{ RouterConfig, Broadcast, SmallestMailboxRouter, DefaultResizer }

import play.api.libs.iteratee.Concurrent.Channel
import play.api.libs.json.JsValue

import sprawler.crawler.url.CrawlerUrl

import scala.concurrent.ExecutionContext
import spray.can.client.{ HostConnectorSettings, ClientConnectionSettings }
import play.api.libs.iteratee.Input

class StreamingLinkQueueMaster(seedUrls: List[CrawlerUrl], channel: Channel[JsValue]) extends LinkQueueMaster(seedUrls) {
  override def handleWorkItemDone() = {
    super.handleWorkItemDone()
    if (urlsLeftToCrawl == 0) {
      channel.end()
    }
  }
}