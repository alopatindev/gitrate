package hiregooddevs.utils

import java.util.concurrent.{Executors, ExecutorService}

import rx.lang.scala.schedulers.ExecutionContextScheduler
import rx.lang.scala.{Observable, Scheduler, Subscription}

import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext

trait RxUtils {

  private val executorService: ExecutorService =
    Executors.newSingleThreadExecutor()
  private val executor: ExecutionContext =
    ExecutionContext.fromExecutor(executorService)
  private val scheduler: Scheduler = ExecutionContextScheduler(executor)

  def observableInterval(duration: Duration): Observable[Long] =
    Observable.interval(duration, scheduler)

  def rxShutdown(): Unit = executorService.shutdownNow()

}
