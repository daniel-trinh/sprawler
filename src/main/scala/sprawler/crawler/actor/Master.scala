package sprawler.crawler.actor

import akka.actor.{ ActorSystem, ActorRef, Actor, Terminated, Props }
import akka.routing._
import akka.actor.Terminated
import akka.routing.Broadcast

import sprawler.crawler.actor.WorkPullingPattern._

import scala.collection.mutable
import scala.concurrent.{ Future, Promise }
import scala.concurrent.ExecutionContext

import scala.Some

/**
 * A 'master' actor that stores a work items of type T in [[sprawler.crawler.actor.Master.workQueue]],
 * and handles orchestration of work between itself and it's [[sprawler.crawler.actor.Worker]]s.
 *
 * A general workflow might look something like this:
 * 1) Master starts up, creates a set of Workers (using a resizable [[akka.routing.Router]]
 *    as the worker pool).
 * 2) Master receives a [[sprawler.crawler.actor.WorkPullingPattern.Work]] message,
 *    and sends a [[sprawler.crawler.actor.WorkPullingPattern.WorkAvailable]] message to the router,
 *    which in turn will forward the message to one of the Workers.
 * 3) Workers receive the message, and send a [[sprawler.crawler.actor.WorkPullingPattern.GimmeWork]] to Master.
 * 4) Master receives several 'GimmeWork' requests, sends a [[sprawler.crawler.actor.WorkPullingPattern.Work]]
 *    message to the sender, one for every work item of type T that is in the work queue.
 *    If a worker does not get a work item, nothing happens -- the next time some work is available
 *    the Master will notify one if it's workers.
 *
 * See [[sprawler.crawler.actor.WorkPullingPattern]] for the types of messages that are
 * handled between this master and it's [[sprawler.crawler.actor.Worker]] actors.
 *
 *
 * This class is intended to be used with an Akka Router [[akka.actor.ActorRef]], with [[sprawler.crawler.actor.Worker]]
 * as the intended [[akka.actor.Props]] for the worker.
 *
 * Intended to act as a dynamically resizable pool of Workers, to allow for concurrent crawling.
 *
 * [[akka.routing.RoundRobinRouter]] or [[akka.routing.SmallestMailboxRouter]] should be used -- other
 * routers might not function properly.
 *
 * @tparam T The type of work being processed.
 */
trait Master[T] extends Actor {
  implicit val ec = context.dispatcher

  /**
   * A Router [[akka.actor.ActorRef]], with [[sprawler.crawler.actor.Worker]] as the intended [[akka.actor.Props]].
   * Intended to act as a dynamically resizable pool of Workers.
   *
   * Should use [[akka.routing.RoundRobinRouter]] or [[akka.routing.SmallestMailboxRouter]] -- other
   * routers might not function properly.
   *
   * Intended to be set through the [[sprawler.crawler.actor.WorkPullingPattern.RegisterWorkerRouter]] message.
   */
  var workers: Promise[ActorRef] = Promise[ActorRef]()

  // Because these vars can only be updated from within this actor,
  // and actors can only process one message at a time, we don't have
  // to worry about race conditions (and don't have to use something like
  // Akka Agents).
  private var workQueue: mutable.Queue[T] = mutable.Queue[T]()

  def handleGimmeWork: Receive = {
    case GimmeWork =>
      if (!workQueue.isEmpty) {
        sender ! Work(workQueue.dequeue())
      }
  }

  /*
  * This default implementation enqueues the provided item of work in the
  * [[sprawler.crawler.actor.Master.workQueue]], and notifies one of the
  * workers that more work is available.
  */
  def handleWork: Receive = {
    case workToAdd: Work[T] =>
      workQueue.enqueue(workToAdd.work)
      workers.future map { _ ! WorkAvailable }
  }

  def handleWorkItemDone: Receive = {
    case WorkItemDone => ()
  }

  def handleTerminated: Receive = {
    case Terminated(actorRef) => ()
  }

  /**
   * This default implementation completes the worker promise, so that
   * other code waiting on the worker router to be registered can continue.
   */
  def handleRegisterWorkerRouter: Receive = {
    case RegisterWorkerRouter(workerRouter) =>
      workers.success(workerRouter)
  }

  /**
   * Handles the messages defined in [[sprawler.crawler.actor.WorkPullingPattern]]
   *
   * Individual handlers in this receive can be overridden / chained when this trait is
   * mixed in elsewhere:
   * {{{
   *   trait LoggedMaster[T] extends Master[T] {
   *     override def handleWork: Receive = {
   *       case workToAdd: Work[T] =>
   *         logger.debug("Work is being added:"+workToAdd.work)
   *         super.handleWork
   *     }
   *   }
   * }}}
   */
  def receive = {
    handleGimmeWork orElse
      handleWork orElse
      handleWorkItemDone orElse
      handleTerminated orElse
      handleRegisterWorkerRouter
  }
}