// Copyright 2017 Alexander Lopatin
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package utils

import org.apache.log4j.{Level, Logger, LogManager}

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

trait LogUtils {

  @transient lazy val log: Logger =
    LogManager.getLogger(getClass.getSimpleName)

  val maxDebugLength = 2000

  def logError(throwable: Throwable): Unit = log.error(throwable.getMessage, throwable)

  def logError(data: Any = "", cut: Boolean = true): Unit =
    if (log.isEnabledFor(Level.ERROR)) { log.error(formatData(data, cut)) }

  def logInfo(data: Any = "", cut: Boolean = true): Unit =
    if (log.isInfoEnabled) { log.info(formatData(data, cut)) }

  def logDebug(data: Any = "", cut: Boolean = true): Unit =
    if (log.isDebugEnabled) { log.debug(formatData(data, cut)) }

  private val methodNesting = 5

  private def currentMethodName(): String =
    Thread.currentThread
      .getStackTrace()(methodNesting)
      .getMethodName
      .split("\\$")
      .last

  private def formatData(data: Any, cut: Boolean): String = {
    val prompt = currentMethodName()
    val dataString = if (cut) cutLongData(data) else data.toString
    val text = if (dataString.isEmpty) "" else s": $dataString"
    s"$prompt$text"
  }

  private def cutLongData(data: Any): String =
    data.toString.take(maxDebugLength)

  implicit class TryLogged[T](t: Try[T]) {
    def logErrors(): Try[T] = {
      t.failed.foreach(throwable => logError(throwable))
      t
    }
  }

  implicit class FutureLogged[T](future: Future[T]) {
    def logErrors()(implicit ec: ExecutionContext): Future[T] = {
      future.failed.foreach(throwable => logError(throwable))
      future
    }
  }

}
