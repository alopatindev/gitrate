package utils

import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext
import scala.concurrent.{Await, Future}
import scala.util.{Success, Try}

object ConcurrencyUtils {

  def filterSucceedFutures[T](xs: Iterable[Future[Try[T]]], timeout: Duration)(
      implicit ec: ExecutionContext): Iterable[T] = {
    val future = Future
      .sequence(xs)
      .map(_.collect { case Success(x) => x })
    Await.result(future, timeout)
  }

}
