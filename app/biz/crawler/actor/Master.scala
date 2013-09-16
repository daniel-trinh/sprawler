package biz.crawler.actor

import akka.actor.{ ActorSystem, ActorRef, Actor }

import biz.concurrency.FutureActions._
import biz.config.CrawlerConfig

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits._

// If the queue is empty for more than the time it takes for a http request to time out * the # of retries,
// the crawler has probably been completed, so it should be ok to shut down the master actor and workers.
class Master[T] extends Actor {
  import WorkPullingPattern._

  val workers = mutable.Set.empty[ActorRef]

  // Because these vars can only be updated from within this actor,
  // and actors can only process one message at a time, we don't have
  // to worry about race conditions (and don't have to use something like
  // Akka Agents).
  private var urlQueue: mutable.Queue[T] = mutable.Queue[T]()
  private var deathTimer: Boolean = false

  def receive = {
    case workQueue: mutable.Queue[T] =>
    // TODO: should use varargs here instead of two messages? or just accept list?
    case url: AddOneToQueue[T] =>
      urlQueue.enqueue(url.work)
      workers foreach { _ ! WorkAvailable }
      deathTimer = false
    case urls: AddManyToQueue[T] =>
      urls.work.foreach { url =>
        urlQueue.enqueue(url)
      }
      deathTimer = false
      workers foreach { _ ! WorkAvailable }
    case RegisterWorker(worker) =>
      play.Logger.info(s"worker $worker registered")
      context.watch(worker)
      workers += worker
    case Terminated(worker) =>
      play.Logger.info(s"worker $worker died - taking off the set of workers")
      workers.remove(worker)

    case GimmeWork =>
      if (urlQueue.isEmpty) {
        play.Logger.info("workers asked for work but we've no more work to do")
        // Start timer to shut down actors
        futureTask(
          message = {
            deathTimer = true
          },
          duration = CrawlerConfig.defaultCrawlDelay.milliseconds
        )
      } else {
        sender ! Work(urlQueue.dequeue())
      }
  }
}