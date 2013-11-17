package sprawler.actors

import akka.testkit.{ TestProbe, ImplicitSender, TestKit }
import akka.actor.{ ActorRef, PoisonPill, ActorSystem, Props }
import akka.routing.{ SmallestMailboxRouter, DefaultResizer, Broadcast }

import sprawler.DummyTestServer
import sprawler.crawler.actor.{ LinkScraper, LinkQueueMaster, LinkScraperWorker }
import sprawler.crawler.actor.WorkPullingPattern._
import sprawler.crawler.url.{ CrawlerUrl, AbsoluteUrl }
import sprawler.SpecHelper

import org.scalatest.{ WordSpecLike, ShouldMatchers, BeforeAndAfter, BeforeAndAfterAll }

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success, Try }
import scala.concurrent.ExecutionContext.Implicits.global

import spray.http.{ HttpResponse, Uri }
import spray.can.Http
import sprawler.actors.LinkActorsSpec.UncrawlableLinkScraper

class LinkActorsSpec(_system: ActorSystem)
    extends TestKit(_system)
    with ImplicitSender
    with WordSpecLike
    with BeforeAndAfter
    with BeforeAndAfterAll
    with ShouldMatchers {

  override def beforeAll() {
    DummyTestServer.startTestServer()
  }
  override def afterAll() {
    DummyTestServer.shutdownTestServer(system)
  }

  def this() = this(ActorSystem("CrawlerSystem"))

  "LinkQueueMaster" should {
    val crawlerUrl = AbsoluteUrl(
      uri = Uri(SpecHelper.testDomain+"/redirectOnce")
    )

    "schedule initial link to be crawled to workers" in {
      LinkActorsSpec.setupMaster(self, crawlerUrl) { master =>
        expectMsg(WorkAvailable)
      }
    }
    "send work to worker on GimmeWork msg" in {

      LinkActorsSpec.setupMaster(self, crawlerUrl) { master =>
        expectMsg(WorkAvailable)

        master ! GimmeWork

        expectMsg(Work(crawlerUrl))
      }
    }
    "should enqueue urls for more crawling" in {
      LinkActorsSpec.setupMaster(self, crawlerUrl) { master =>
        expectMsg(WorkAvailable)
        master ! GimmeWork
        expectMsg(Work(crawlerUrl))

        // Note: we're sending the same URL twice because URL duplication
        // isn't checked in the master actor, it's done in the worker.
        // This is making sure URLS that are sent to the master outside
        // of constructor / initialization get queued properly.
        master ! Work(crawlerUrl)

        expectMsg(WorkAvailable)
        master ! GimmeWork
        expectMsg(Work(crawlerUrl))
      }
    }
    "should shutdown self and workers when no work is left" in {
      LinkActorsSpec.setupMaster(self, crawlerUrl) { master =>
        expectMsg(WorkAvailable)

        master ! GimmeWork

        expectMsg(Work(crawlerUrl))

        master ! WorkItemDone

        expectMsg(Broadcast(PoisonPill))
      }
    }
  }

  "LinkScraperWorker" should {

    // Make these available in scope for the tests.
    // Before block initializes them to empty buffers.
    var urlsCrawled: mutable.ArrayBuffer[String] = null
    var urlsFailed: mutable.ArrayBuffer[String] = null

    "handle single redirect" in {
      val crawlerUrl = AbsoluteUrl(
        uri = Uri(SpecHelper.testDomain+"/redirectOnce")
      )

      LinkActorsSpec.setupWorker(propArgs = Seq(self, crawlerUrl)) { workerRouter =>
        expectMsg(GimmeWork)

        workerRouter ! Work(crawlerUrl)

        expectMsg(Work(crawlerUrl.nextUrl(SpecHelper.testDomain+"/relativeUrl", redirects = Some(4))))

        expectMsg(WorkItemDone)
        expectMsg(GimmeWork)
      }
    }

    "not schedule already crawled urls" in {
      val crawlerUrl = AbsoluteUrl(
        uri = Uri(SpecHelper.testDomain+"/redirectOnce")
      )

      LinkActorsSpec.setupWorker(propArgs = Seq(self, crawlerUrl)) { workerRouter =>
        expectMsg(GimmeWork)

        workerRouter ! Work(crawlerUrl)

        expectMsg(Work(crawlerUrl.nextUrl(SpecHelper.testDomain+"/relativeUrl", redirects = Some(4))))

        expectMsg(WorkItemDone)
        expectMsg(GimmeWork)

        workerRouter ! Work(crawlerUrl)

        expectMsg(WorkItemDone)
        expectMsg(GimmeWork)
      }
    }

    "handle infinite redirect (redirect limit reached)" in {

      val redirectCrawlerUrl = AbsoluteUrl(
        uri = Uri(SpecHelper.testDomain+"/redirectForever/1234"),
        redirectsLeft = Some(0)
      )

      var uncrawlableLinks = mutable.ArrayBuffer[CrawlerUrl]()

      LinkActorsSpec.setupWorker(
        workerClass = Some(classOf[UncrawlableLinkScraper]),
        propArgs = Seq(self, redirectCrawlerUrl, uncrawlableLinks)
      ) { workerRouter =>
          expectMsg(GimmeWork)

          workerRouter ! Work(redirectCrawlerUrl)

          // expectMsg checks messages in the order they are received, dequeueing
          // them one at a time each time expectMsg is called.
          // This will throw an exception if Work(...) is received instead of
          // WorkItemDone.
          expectMsg(WorkItemDone)
          expectMsg(GimmeWork)

          uncrawlableLinks shouldBe mutable.ArrayBuffer[CrawlerUrl](redirectCrawlerUrl)
        }
    }
    "send links to be crawled to master" in {

      val crawlerUrl = AbsoluteUrl(
        uri = Uri(SpecHelper.testDomain+"/"),
        depth = 10)

      LinkActorsSpec.setupWorker(propArgs = Seq(self, crawlerUrl)) { workerRouter =>
        expectMsg(GimmeWork)

        workerRouter ! Work(crawlerUrl)

        expectMsg(Work(crawlerUrl.nextUrl(SpecHelper.testDomain+"/relativeUrl")))
        expectMsg(Work(crawlerUrl.nextUrl(SpecHelper.testDomain+"/relativeUrlMissingSlash")))
        expectMsg(Work(crawlerUrl.nextUrl(SpecHelper.testDomain+"/")))
        expectMsg(Work(crawlerUrl.nextUrl(SpecHelper.testDomain+"/relativeUrl")))
        expectMsg(Work(crawlerUrl.nextUrl(SpecHelper.testDomain+"/redirectForever")))
        expectMsg(Work(crawlerUrl.nextUrl(SpecHelper.testDomain+"/redirectOnce")))
        expectMsg(Work(crawlerUrl.nextUrl(SpecHelper.testDomain+"/fullUri")))
        expectMsg(Work(crawlerUrl.nextUrl(SpecHelper.testDomain+"/nestedOnce")))

        expectMsg(WorkItemDone)
        expectMsg(GimmeWork)
      }
    }
  }
}

object LinkActorsSpec {

  class UncrawlableLinkScraper(
      masterRef: ActorRef,
      url: CrawlerUrl,
      uncrawlableLinks: mutable.ArrayBuffer[CrawlerUrl]) extends LinkScraperWorker(masterRef, url) {
    override def onUrlNotCrawlable = (url, error) => {
      uncrawlableLinks += url
    }
  }

  val resizer = DefaultResizer(lowerBound = 1, upperBound = 10)
  val router = SmallestMailboxRouter(nrOfInstances = 1, resizer = Some(resizer))

  def setupWorker[T, U <: LinkScraperWorker](
    workerClass: Option[Class[U]] = None,
    propArgs: Seq[Any])(f: ActorRef => T)(implicit system: ActorSystem): T = {

    val clazz = workerClass.getOrElse {
      classOf[LinkScraperWorker]
    }

    val props = Props(clazz, propArgs: _*)
    val workerRouter = system.actorOf(props.withRouter(router))

    f(workerRouter)
  }

  def setupMaster[T](workerRef: ActorRef, url: CrawlerUrl)(f: ActorRef => T)(implicit system: ActorSystem): T = {
    val masterProps = Props(classOf[LinkQueueMaster], List(url))
    val master = system.actorOf(masterProps)
    master ! RegisterWorkerRouter(workerRef)

    f(master)
  }
}