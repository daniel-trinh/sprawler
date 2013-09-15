package biz.crawler.actor

import akka.actor.ActorRef

object WorkPullingPattern {
  sealed trait Message
  case object GimmeWork extends Message
  case object CurrentlyBusy extends Message
  case object WorkAvailable extends Message
  case class Terminated(worker: ActorRef) extends Message
  case class AddOneToQueue[T](work: T) extends Message
  case class AddManyToQueue[T](work: Iterable[T]) extends Message
  case class RegisterWorker(worker: ActorRef) extends Message
  case class Work[T](work: T) extends Message
}