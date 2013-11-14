package sprawler.concurrency

import scala.concurrent.{ ExecutionContext, Promise, Future }
import scala.util.{ Try, Failure, Success }

object FutureImplicits {

  /**
   * Adds a 'tryMe' method on [[scala.concurrent.Future]] that converts a
   * Future[T] into a Future[Try[T]]
   *
   * @param future The future to wrap in a Try
   * @tparam T Type contained within future
   */
  implicit class RichFuture[T](future: Future[T])(implicit ctx: ExecutionContext) {
    def tryMe: Future[Try[T]] = {
      val p = Promise[Try[T]]()
      future.onComplete { x =>
        p.complete(Try(x))
      }
      p.future
    }
  }

  /**
   * Adds a 'asFuture' method on [[scala.util.Try]] that converts a
   * Try[T] into a Future[T]
   * @param attempt The Try[T] to convert to a Future[T]
   * @tparam T Type contained within future
   */
  implicit class TryAsFuture[T](val attempt: Try[T]) extends AnyVal {
    def asFuture: Future[T] = attempt match {
      case Success(v) => Future successful v
      case Failure(f) => Future failed f
    }
  }

}