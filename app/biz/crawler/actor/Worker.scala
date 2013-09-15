package biz.crawler.actor

import akka.actor.{ ActorRef, Actor }

import scala.concurrent.Future
import scala.collection.mutable
import scala.reflect.ClassTag

abstract class Worker[T: ClassTag](val master: ActorRef) extends Actor {
  import WorkPullingPattern._
  implicit val ec = context.dispatcher

  override def preStart() {
    master ! RegisterWorker(self)
    master ! GimmeWork
  }

  def receive = {
    case WorkAvailable ⇒
      master ! GimmeWork
    case Work(work: T) ⇒
      doWork(work) onComplete { case _ => master ! GimmeWork }
  }

  def doWork(work: T): Future[_]
}
