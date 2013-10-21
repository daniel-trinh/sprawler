package biz.crawler.actor

import akka.actor.ActorRef

object WorkPullingPattern {
  sealed trait Message

  /**
   * Sent from Worker to Master
   *
   */
  case object GimmeWork extends Message

  /**
   * Sent from Master to Workers
   * Notifies Workers that there are work items to process.
   */
  case object WorkAvailable extends Message

  /**
   * Sent from Worker to Master
   * Enqueues work to be done in Master's internal queue.
   *
   * @param work Work item to enqueue for processing
   * @tparam T type of work to process
   */
  case class Work[T](work: T) extends Message

  /**
   * Sent from Worker to Master
   * Used to notify Master of a work item being completed.
   */
  case object WorkItemDone extends Message

  /**
   * Sent to Master, from wherever the workers Router is defined / accessible.
   * Used to register with Master, so Master can shut down workers when there
   * is no work left.
   *
   * @param workers The [[biz.crawler.actor.Worker]] Router instance.
   */
  case class RegisterWorkers(workers: ActorRef) extends Message
}