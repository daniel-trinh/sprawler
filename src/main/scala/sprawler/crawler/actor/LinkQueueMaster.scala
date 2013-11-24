package sprawler.crawler.actor

import akka.actor.{ Props, PoisonPill }
import akka.routing.{ RouterConfig, Broadcast, SmallestMailboxRouter, DefaultResizer }

import sprawler.crawler.url.CrawlerUrl

import scala.concurrent.ExecutionContext
import spray.can.client.{ HostConnectorSettings, ClientConnectionSettings }

class LinkQueueMaster(seedUrls: List[CrawlerUrl]) extends Master[CrawlerUrl] {
  import WorkPullingPattern._

  // Start the crawling process
  seedUrls.foreach { url =>
    self ! Work(url)
  }

  protected var urlsLeftToCrawl = 0

  override def handleWorkItemDone() {
    super.handleWorkItemDone()
    urlsLeftToCrawl -= 1

    // We can't simply check to see if the queue is empty, because a link crawling worker actor might
    // still be processing a url -- we wait for the worker to tell us that it has finished
    // crawling a URL before considering a URL done.
    if (urlsLeftToCrawl == 0) {
      workers.future map { _ ! Broadcast(PoisonPill) }
      self ! PoisonPill
    }
  }

  override def handleWork(work: Work[CrawlerUrl]) {
    super.handleWork(work)
    urlsLeftToCrawl += 1
  }
}