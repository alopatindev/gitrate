package hiregooddevs.utils

import org.apache.log4j.{Level, Logger, LogManager}

trait LogUtils {

  @transient lazy val log: Logger =
    LogManager.getLogger(getClass.getSimpleName)

  val maxDebugLength = 100

  def logError(data: Any): Unit =
    if (log.isEnabledFor(Level.ERROR)) log.error(formatData(data))

  def logInfo(data: Any): Unit =
    if (log.isInfoEnabled) log.info(formatData(data))

  def logDebug(data: Any): Unit =
    if (log.isDebugEnabled) log.debug(formatData(data))

  private val methodNesting = 5

  private def currentMethodName(): String =
    Thread.currentThread
      .getStackTrace()(methodNesting)
      .getMethodName
      .split("\\$")
      .last

  private def formatData(data: Any): String =
    s"${currentMethodName()} ${cutLongData(data)}"

  private def cutLongData(data: Any): String =
    data.toString.take(maxDebugLength)

}
