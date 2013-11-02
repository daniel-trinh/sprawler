package biz.actors

import akka.testkit.{ TestProbe, ImplicitSender, TestKit }
import akka.actor.{ ActorRef, PoisonPill, ActorSystem, Props }
import akka.routing.{ SmallestMailboxRouter, DefaultResizer, Broadcast }

import biz.DummyTestServer
import biz.crawler.actor.{ LinkQueueMaster, LinkScraperWorker }
import biz.crawler.actor.WorkPullingPattern._
import biz.crawler.url.{ CrawlerUrl, AbsoluteUrl }
import biz.SpecHelper

import org.scalatest.{ ShouldMatchers, WordSpec, BeforeAndAfter, BeforeAndAfterAll }

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success, Try }
import scala.concurrent.ExecutionContext.Implicits.global

import spray.http.{ HttpResponse, Uri }
import spray.can.Http

class DeadLinkSpec(_system: ActorSystem)
    extends TestKit(_system)
    with ImplicitSender
    with WordSpec
    with BeforeAndAfter
    with BeforeAndAfterAll
    with ShouldMatchers {

  override def beforeAll {
    DummyTestServer.startTestServer()
  }
  override def afterAll {
    DummyTestServer.shutdownTestServer(system)
  }

  def this() = this(ActorSystem("CrawlerSystem"))

  "LinkQueueMaster" should {
    val crawlerUrl = AbsoluteUrl(
      uri = Uri(SpecHelper.testDomain+"/redirectOnce")
    )

    "schedule initial link to be crawled to workers" in {
      DeadLinkSpec.setupMaster(self, crawlerUrl) { master =>
        expectMsg(WorkAvailable)
      }
    }
    "send work to worker on GimmeWork msg" in {

      DeadLinkSpec.setupMaster(self, crawlerUrl) { master =>
        expectMsg(WorkAvailable)

        master ! GimmeWork

        expectMsg(Work(crawlerUrl))
      }
    }
    "should enqueue urls for more crawling" in {
      DeadLinkSpec.setupMaster(self, crawlerUrl) { master =>
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
      DeadLinkSpec.setupMaster(self, crawlerUrl) { master =>
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

      DeadLinkSpec.setupWorker(self, crawlerUrl) { workerRouter =>
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

      DeadLinkSpec.setupWorker(self, crawlerUrl) { workerRouter =>
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

      def onNotCrawlable(url: CrawlerUrl, reason: Throwable) {
        uncrawlableLinks += url
      }

      DeadLinkSpec.setupWorker(self, redirectCrawlerUrl, onNotCrawlable = onNotCrawlable) { workerRouter =>
        expectMsg(GimmeWork)

        workerRouter ! Work(redirectCrawlerUrl)

        // expectMsg checks messages in the order they are received, dequeueing
        // them one at a time each time expectMsg is called.
        // This will throw an exception if Work(...) is received instead of
        // WorkItemDone.
        expectMsg(WorkItemDone)
        expectMsg(GimmeWork)

        uncrawlableLinks should be === mutable.ArrayBuffer[CrawlerUrl](redirectCrawlerUrl)
      }
    }
    "send links to be crawled to master" in {

      val crawlerUrl = AbsoluteUrl(
        uri = Uri(SpecHelper.testDomain+"/"),
        depth = 10)

      DeadLinkSpec.setupWorker(self, crawlerUrl) { workerRouter =>
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

object DeadLinkSpec {

  val resizer = DefaultResizer(lowerBound = 1, upperBound = 10)
  val router = SmallestMailboxRouter(nrOfInstances = 1, resizer = Some(resizer))

  def onUrlComplete(url: CrawlerUrl, response: Try[HttpResponse]) {
    response match {
      case Success(httpResponse) =>
        println(s"url successfully fetched: ${url.uri.toString()}")
      case Failure(error) =>
        println("error:"+error.getMessage)
    }
  }

  def onNotCrawlable(url: CrawlerUrl, reason: Throwable) {
    println("NOT CRAWLABLE:"+reason.toString)
  }

  def setupWorker[T](
    masterRef: ActorRef,
    url: CrawlerUrl,
    onUrlComplete: (CrawlerUrl, Try[HttpResponse]) => Unit = onUrlComplete,
    onNotCrawlable: (CrawlerUrl, Throwable) => Unit = onNotCrawlable)(f: ActorRef => T)(implicit system: ActorSystem): T = {

    val workerProps = Props(classOf[LinkScraperWorker],
      masterRef,
      url,
      onUrlComplete,
      onNotCrawlable
    )

    val workerRouter = system.actorOf(workerProps.withRouter(router))

    f(workerRouter)
  }

  def setupMaster[T](workerRef: ActorRef, url: CrawlerUrl)(f: ActorRef => T)(implicit system: ActorSystem): T = {
    val masterProps = Props(classOf[LinkQueueMaster], List(url))
    val master = system.actorOf(masterProps)
    master ! RegisterWorkers(workerRef)

    f(master)
  }
}