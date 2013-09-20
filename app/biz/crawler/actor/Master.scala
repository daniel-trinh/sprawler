package biz.crawler.actor

import akka.actor.{ ActorSystem, ActorRef, Actor, Terminated }

import biz.crawler.actor.WorkPullingPattern._
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

  val workers = mutable.Set.empty[ActorRef]

  // Because these vars can only be updated from within this actor,
  // and actors can only process one message at a time, we don't have
  // to worry about race conditions (and don't have to use something like
  // Akka Agents).
  private var workQueue: mutable.Queue[T] = mutable.Queue[T]()

  def receive = {
    case workToAdd: Work[T] =>
      onWork(workToAdd.work)
      workHook()

    case RegisterWorker(worker) =>
      onRegisterWorker(worker)
      registerWorkerHook()

    case Terminated(worker) =>
      onTerminated(worker)
      terminatedHook()

    case GimmeWork =>
      onGimmeWork()
      gimmeWorkHook()

    case WorkItemDone =>
      workItemDoneHook()
  }

  def onWork(workToAdd: T) {
    workQueue.enqueue(workToAdd)
    workers foreach { _ ! WorkAvailable }
  }

  def onRegisterWorker(worker: ActorRef) {
    play.Logger.info(s"worker $worker registered")
    context.watch(worker)
    workers += worker
  }

  def onTerminated(worker: ActorRef) {
    play.Logger.info(s"worker $worker died - taking off the set of workers")
    workers.remove(worker)
  }

  def onGimmeWork() {
    if (workQueue.isEmpty) {
      play.Logger.info("workers asked for work but we've no more work to do")
    } else {
      sender ! Work(workQueue.dequeue())
    }
  }

  // Override these methods to execute custom code after each possible message type
  def workItemDoneHook() {}
  def workHook() {}
  def registerWorkerHook() {}
  def terminatedHook() {}
  def gimmeWorkHook() {}
}