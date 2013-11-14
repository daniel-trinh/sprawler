package sprawler.crawler.actor

import akka.actor.{ ActorRef, Actor }

import scala.concurrent.Future
import scala.reflect.ClassTag

/**
 * An actor designed to process a single piece of work item, and then ask a [[sprawler.crawler.actor.Master]]
 * actor for more work. The act of "asking" or "pulling" work from the master actor is intended
 * to prevent any worker actor from being flooded with work.
 *
 * See [[sprawler.crawler.actor.WorkPullingPattern]] for the types of messages that are
 * handled between this worker and [[sprawler.crawler.actor.Master]].
 *
 * @param master [[sprawler.crawler.actor.Master]] Actor that handles storing and orchestrating
 *               work between itself and it's worker actors.
 *
 * @tparam T The type of work being processed.
 */
abstract class Worker[T: ClassTag](val master: ActorRef) extends Actor {
  import WorkPullingPattern._

  implicit val ec = context.dispatcher

  override def preStart() {
    master ! GimmeWork
  }

  def receive = {
    case WorkAvailable ⇒
      master ! GimmeWork
    case Work(work: T) ⇒
      doWork(work) onComplete { case _ => master ! GimmeWork }
  }

  /**
   * Processes a single work item.
   *
   * @param work The actual piece of work to process.
   * @return A future of any type. When the work is complete, the master actor is notified
   *         that this worker is ready for more work.
   */
  def doWork(work: T): Future[_]
}