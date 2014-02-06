package sprawler.concurrency

import akka.actor.ActorSystem

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future, Promise }

/**
 * Extra useful concurrency related stuff to help with common concurrency tasks
 */
object FutureActions {

  /**
   * A port of play's Promise.timeout using Akka's scheduler system (based on Netty's HashedWheelTimer).
   * Should handle more schedules than Play's Promise.timeout, which is based off of a thread-blocking Java scheduler.
   *
   * @param message The message to fill the Future with
   * @param duration How long to wait before future is filled with duration
   * @param system An actor system in which the schedule is run, defaults to play's Akka.system
   * @param ec Execution context in which to run the schedule
   * @tparam A The type of the param message
   * @return A future completed with message after duration
   */
  def futureTask[A](message: => A, duration: FiniteDuration)(implicit system: ActorSystem, ec: ExecutionContext): Future[A] = {
    val p = Promise[A]()
    system.scheduler.scheduleOnce(delay = duration) {
      p.completeWith(Future.successful(message))
    }
    p.future
  }
}