package biz.concurrency

import scala.concurrent.{ ExecutionContext, Promise, Future }
import scala.util.Try

object FutureImplicits {
  implicit class RichFuture[T](future: Future[T])(implicit ctx: ExecutionContext) {
    def tryMe: Future[Try[T]] = {
      val p = Promise[Try[T]]()
      future.onComplete { x =>
        p.complete(Try(x))
      }
      p.future
    }
  }
}