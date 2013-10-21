package biz.actors

import akka.testkit.{ TestProbe, ImplicitSender, TestKit }
import akka.actor.{ PoisonPill, ActorSystem, Props }
import akka.routing.{ SmallestMailboxRouter, DefaultResizer, Broadcast }

import biz.DummyTestServer
import biz.crawler.actor.{ LinkQueueMaster, LinkScraperWorker }
import biz.crawler.actor.WorkPullingPattern._
import biz.crawler.url.{ CrawlerUrl, AbsoluteUrl }

import org.scalatest.{ ShouldMatchers, WordSpec, BeforeAndAfter }

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._

import spray.http.{ HttpResponse, Uri }
import scala.util.{ Failure, Success, Try }
import spray.can.Http
import java.net.InetSocketAddress

class DeadLinkSpec(_system: ActorSystem)
    extends TestKit(_system)
    with ImplicitSender
    with WordSpec
    with BeforeAndAfter
    with ShouldMatchers {

  def this() = this(ActorSystem("CrawlerSystem"))

  "LinkScraperWorker" should {
    before {
      val wtf = Await.result(DummyTestServer.startTestServer(system), 5.seconds)
    }

    "crawl a simple link" in {

      val crawlerUrl = AbsoluteUrl(
        fromUri = Uri("http://localhost:8080/"),
        uri = Uri("http://localhost:8080/"),
        depth = 10)

      var urlsCrawled = mutable.ArrayBuffer[String]()
      var urlsFailed = mutable.ArrayBuffer[String]()

      def onUrlComplete(url: CrawlerUrl, response: Try[HttpResponse]) {
        response match {
          case Success(httpResponse) =>
            play.Logger.info(s"url successfully fetched: ${url.uri.toString()}")
            urlsCrawled += url.uri.toString()
          case Failure(error) =>
            play.Logger.error(error.getMessage)
            urlsFailed += url.uri.toString()
        }
      }

      def onNotCrawlable(url: CrawlerUrl, reason: Try[Unit]) {
        reason match {
          case Success(_)     => "do nothing"
          case Failure(error) => play.Logger.debug("NOT CRAWLABLE:"+error.toString())
        }
      }

      val workerProps = Props(classOf[LinkScraperWorker],
        self,
        crawlerUrl,
        onUrlComplete _,
        onNotCrawlable _
      )

      val resizer = DefaultResizer(lowerBound = 1, upperBound = 10)
      val router = SmallestMailboxRouter(nrOfInstances = 1, resizer = Some(resizer))

      val workerRouter = system.actorOf(workerProps.withRouter(router), "deadLinkWorkerRouter")

      expectMsg(GimmeWork)

      workerRouter ! Work(crawlerUrl)

      expectMsg(Work(crawlerUrl.nextUrl("http://localhost:8080/relativeUrl")))
      expectMsg(Work(crawlerUrl.nextUrl("http://localhost:8080/relativeUrlMissingSlash")))
      expectMsg(Work(crawlerUrl.nextUrl("http://localhost:8080/")))
      expectMsg(Work(crawlerUrl.nextUrl("http://localhost:8080/relativeUrl")))
      expectMsg(Work(crawlerUrl.nextUrl("http://localhost:8080/redirectForever")))
      expectMsg(Work(crawlerUrl.nextUrl("http://localhost:8080/redirectOnce")))
      expectMsg(Work(crawlerUrl.nextUrl("http://localhost:8080/fullUri")))
      expectMsg(Work(crawlerUrl.nextUrl("http://localhost:8080/nestedOnce")))

      expectMsg(WorkItemDone)
      expectMsg(GimmeWork)

      workerRouter ! Work(crawlerUrl.nextUrl("http://localhost:8080/"))

      urlsCrawled should be === mutable.ArrayBuffer("http://localhost:8080/")
      urlsFailed should be === mutable.ArrayBuffer()
      // Worker router should receive these
      // expectMsg(Work(crawlerUrl))

      // Master should be notified of successful crawl
      // expectMsg(WorkItemDone)

      // Master should shut down worker router, and shut itself down as well
      // expectMsg(Broadcast(PoisonPill))
      // expectMsg(PoisonPill)
    }
  }
}