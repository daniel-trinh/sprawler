package biz.crawler.actor

import akka.actor.{ ActorSystem, ActorRef, Actor, Terminated, Props }
import akka.routing._
import akka.actor.Terminated
import akka.routing.Broadcast

import biz.crawler.actor.WorkPullingPattern._
import biz.crawler.actor.WorkPullingPattern.Work

import scala.collection.mutable
import scala.concurrent.{ Future, Promise }
import scala.concurrent.ExecutionContext

import scala.Some

/**
 * A 'master' actor that stores a work items of type T in [[biz.crawler.actor.Master.workQueue]],
 * and handles orchestration of work between itself and it's [[biz.crawler.actor.Worker]]s.
 *
 * A general workflow might look something like this:
 * 1) Master starts up, creates a set of Workers (using a resizable [[akka.routing.Router]]
 *    as the worker pool).
 * 2) Master receives a [[biz.crawler.actor.WorkPullingPattern.Work]] message,
 *    and sends a [[biz.crawler.actor.WorkPullingPattern.WorkAvailable]] message to the router,
 *    which in turn will forward the message to one of the Workers.
 * 3) Workers receive the message, and send a [[biz.crawler.actor.WorkPullingPattern.GimmeWork]] to Master.
 * 4) Master receives several 'GimmeWork' requests, sends a [[biz.crawler.actor.WorkPullingPattern.Work]]
 *    message to the sender, one for every work item of type T that is in the work queue.
 *    If a worker does not get a work item, nothing happens -- the next time some work is available
 *    the Master will notify one if it's workers.
 *
 * See [[biz.crawler.actor.WorkPullingPattern]] for the types of messages that are
 * handled between this master and it's [[biz.crawler.actor.Worker]] actors.
 *
 *
 * This class is intended to be used with an Akka Router [[akka.actor.ActorRef]], with [[biz.crawler.actor.Worker]]
 * as the intended [[akka.actor.Props]] for the worker.
 *
 * Intended to act as a dynamically resizable pool of Workers, to allow for concurrent crawling.
 *
 * [[akka.routing.RoundRobinRouter]] or [[akka.routing.SmallestMailboxRouter]] should be used -- other
 * routers might not function properly.
 *
 * @tparam T The type of work being processed.
 */
abstract class Master[T] extends Actor {
  implicit val ec = context.dispatcher

  /**
   * A Router [[akka.actor.ActorRef]], with [[biz.crawler.actor.Worker]] as the intended [[akka.actor.Props]].
   * Intended to act as a dynamically resizable pool of Workers.
   *
   * Should use [[akka.routing.RoundRobinRouter]] or [[akka.routing.SmallestMailboxRouter]] -- other
   * routers might not function properly.
   *
   * Intended to be set through the [[biz.crawler.actor.WorkPullingPattern.RegisterWorkers]] message.
   */
  var workers: Promise[ActorRef] = Promise[ActorRef]()

  // Because these vars can only be updated from within this actor,
  // and actors can only process one message at a time, we don't have
  // to worry about race conditions (and don't have to use something like
  // Akka Agents).
  private var workQueue: mutable.Queue[T] = mutable.Queue[T]()

  def receive = {
    case workToAdd: Work[T] =>
      onWork(workToAdd.work)
      workHook()

    case Terminated(worker) =>
      onTerminated(worker)
      terminatedHook()

    case GimmeWork =>
      onGimmeWork()
      gimmeWorkHook()

    case WorkItemDone =>
      workItemDoneHook()

    case RegisterWorkers(workerRouter) =>
      registerWorkersHook(workerRouter)
  }

  /**
   * Callback, moved out of the 'receive' method to allow for overriding.
   *
   * This default implementation enqueues the provided item of work in the
   * [[biz.crawler.actor.Master.workQueue]], and notifies one of the
   * workers that more work is available.
   *
   * @param workToAdd The single work item to add to the queue.
   */
  def onWork(workToAdd: T) {
    workQueue.enqueue(workToAdd)
    workers.future map { _ ! WorkAvailable }
  }

  def onTerminated(worker: ActorRef) {}

  def onGimmeWork() {
    if (!workQueue.isEmpty) {
      sender ! Work(workQueue.dequeue())
    }
  }

  def registerWorkersHook(workerRouter: ActorRef) {
    workers.success(workerRouter)
  }

  // Override these methods to execute custom code after each possible message type
  def workItemDoneHook() {}
  def workHook() {}
  def terminatedHook() {}
  def gimmeWorkHook() {}
}