package scrawler.crawler.actor

import akka.actor.{ ActorRef, Actor }

import sprawler.crawler.actor.WorkPullingPattern._

import scala.collection.mutable

object MasterActorTraits {
  // trait WorkToAddTrait[T] {
  //   def workQueue: mutable.Queue[T]

  //   override def receive = {
  //     case workToAdd: Work[T] =>
  //       workQueue.enqueue(workToAdd.work)
  //       super.receive(x)
  //   }
  // }

  // trait RegisterWorkersTrait[T] extends ActorStack {
  //   override def receive = {

  //   }
  // }

  // trait GimmeWorkTrait[T] extends ActorStack {
  //   def workQueue: mutable.Queue[T]

  //   override def receive = {
  //     if (!workQueue.isEmpty) {
  //       sender ! Work(workQueue.dequeue())
  //     }
  //   }
  // }
}

trait CrawlerUrlMaster extends ActorStack {

  def workers
  var urlsLeftToCrawl: Int

  override def receive = {
    case WorkItemDone =>
      urlsLeftToCrawl -= 1

      super.receive(WorkItemDone)
      // We can't simply check to see if the queue is empty, because a link crawling worker actor might
      // still be processing a url -- we wait for the worker to tell us that it has finished
      // crawling a URL before considering a URL done. This algorithm does not work
      // in a distributed setting, it would need to be replaced with an ACK based system.
      if (urlsLeftToCrawl == 0) {
        workers.future map { _ ! Broadcast(PoisonPill) }
        self ! PoisonPill
      }
    case msg @ Work(_) =>
      urlsLeftToCrawl += 1
      super.receive(msg)
  }

}

trait ActorStack extends Actor {
  def wrappedReceive: Receive

  def receive: Receive = {
    case x =>
      if (wrappedReceive.isDefinedAt(x))
        wrappedReceive(x)
      else
        unhandled(x)
  }
}

