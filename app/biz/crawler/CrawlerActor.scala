package biz.crawler

import biz.config.CrawlerConfig
import biz.CrawlerExceptions.{ RedirectLimitReachedException, MissingRedirectUrlException, UnprocessableUrlException }

import play.api.libs.iteratee.Concurrent
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{ JsString, JsObject, Json, JsValue }

import akka.actor.{ActorRef, Actor}

import scala.async.Async.{ async, await }
import scala.util.{ Try, Success, Failure }
import scala.{ Some, None }
import spray.http.{ HttpHeader, HttpResponse }
import spray.http.HttpHeaders.Location
import spray.client.pipelining._
import scala.concurrent.Future
import scala.annotation.tailrec
import biz.http.client.HttpCrawlerClient
import scala.collection.mutable
import scala.reflect.ClassTag


object WorkPullingPattern {
  sealed trait Message
  trait Epic[T] extends Iterable[T] //used by master to create work (in a streaming way)
  case object GimmeWork extends Message
  case object CurrentlyBusy extends Message
  case object WorkAvailable extends Message
  case class Terminated(worker: ActorRef) extends Message
  case class RegisterWorker(worker: ActorRef) extends Message
  case class Work[T](work: T) extends Message
}

class Master[T] extends Actor {
  import WorkPullingPattern._

  val workers = mutable.Set.empty[ActorRef]
  var currentEpic: Option[Epic[T]] = None

  def receive = {
    case epic: Epic[T] ⇒
      if (currentEpic.isDefined)
        sender ! CurrentlyBusy
      else if (workers.isEmpty)
        play.Logger.error("Got work but there are no workers registered.")
      else {
        currentEpic = Some(epic)
        workers foreach { _ ! WorkAvailable }
      }

    case RegisterWorker(worker) ⇒
      play.Logger.info(s"worker $worker registered")
      context.watch(worker)
      workers += worker

    case Terminated(worker) ⇒
      play.Logger.info(s"worker $worker died - taking off the set of workers")
      workers.remove(worker)

    case GimmeWork ⇒ currentEpic match {
      case None ⇒
        play.Logger.info("workers asked for work but we've no more work to do")
      case Some(epic) ⇒
        val iter = epic.iterator
        if (iter.hasNext)
          sender ! Work(iter.next)
        else {
          play.Logger.info(s"done with current epic $epic")
          currentEpic = None
        }
    }
  }
}

abstract class Worker[T: ClassTag](val master: ActorRef) extends Actor {
  import WorkPullingPattern._
  implicit val ec = context.dispatcher

  override def preStart {
    master ! RegisterWorker(self)
    master ! GimmeWork
  }

  def receive = {
    case WorkAvailable ⇒
      master ! GimmeWork
    case Work(work: T) ⇒
      // haven't found a nice way to get rid of that warning
      // looks like we can't suppress the erasure warning: http://stackoverflow.com/questions/3506370/is-there-an-equivalent-to-suppresswarnings-in-scala
      doWork(work) onComplete { case _ ⇒ master ! GimmeWork }
  }

  def doWork(work: T): Future[_]
}

class UrlQueueActor(val channel: Concurrent.Channel[JsValue], crawlerActor: CrawlerActor) extends Actor with Streams {
  def receive = {
    // reject urls that are not crawlable
    case url: CrawlerUrl => {
      if (!url.isWithinDepth) {
        // TODO: move this into a crawler exception?
        channel.push(JsObject(
          Seq("complete" -> JsString(s"Maximum crawl depth has been reached. Depth: ${CrawlerConfig.maxDepth}"))
        ))
        cleanup()
      }
    }
    case wtf @ _ => {
      play.Logger.warn(s"Unknown object received in UrlQueueActor: $wtf")
    }
  }
}

class CrawlerActor(val channel: Concurrent.Channel[JsValue], val crawlerUrl: CrawlerUrl)
    extends Actor with Streams with RedirectFollower {
  import CrawlerAgents._

  def receive = {
    case url: CrawlerUrl => {
      play.Logger.info(s"Message received: $url")

      if (!url.isWithinDepth) {
        // TODO: move this into a crawler exception?
        channel.push(JsObject(
          Seq("complete" -> JsString(s"Maximum crawl depth has been reached. Depth: ${CrawlerConfig.maxDepth}"))
        ))
        cleanup()
      } else {
        url.isCrawlable match {
          case Success(true) => {
            val client = getClient(url.uri)

            async {
              // val result = await(HttpCrawlerClient("http://localhost:9000").get("/"))
              val result = await(client.get(url.uri.path.toString()))
              result match {
                case Failure(err) =>
                  play.Logger.debug(s"url failed to fetch")
                  streamJsonErrorFromException(err, eofAndEnd = false)
                case Success(response) =>
                  streamJsonResponse(url.fromUri.toString(), url.uri.toString(), response)
                  play.Logger.debug(s"url successfully fetched: ${url.uri.toString()}")
                  val redirectResult = await(followRedirects(url, Future(Success(response))))

              }
              //              cleanup()
            }
          }
          case Failure(error) => {
            streamJsonErrorFromException(error, eofAndEnd = false)
            cleanup()
          }
        }
      }
    }
    case m @ _ => {
      play.Logger.info(s"Message received")
      play.Logger.error(s"Unexpected message: $m")
    }
  }
}


trait RedirectFollower {
  def crawlerUrl: CrawlerUrl

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
