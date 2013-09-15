package biz.crawler.actor

import akka.actor.{ ActorRef, Actor }
import scala.concurrent.Future
import scala.collection.mutable

class Master[T] extends Actor {
  import WorkPullingPattern._

  val workers = mutable.Set.empty[ActorRef]
  var urlQueue: mutable.Queue[T] = mutable.Queue[T]()

  def receive = {
    case workQueue: mutable.Queue[T] =>
    case url: AddOneToQueue[T] =>
      urlQueue.enqueue(url.work)
    case urls: AddManyToQueue[T] =>
      urls.work.foreach { url =>
        urlQueue.enqueue(url)
      }
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
      } else {
        sender ! Work(urlQueue.dequeue())
      }
  }
}