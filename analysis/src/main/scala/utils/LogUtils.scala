package hiregooddevs.utils

import org.apache.log4j.{Level, Logger, LogManager}

trait LogUtils {

  @transient lazy val log: Logger =
    LogManager.getLogger(getClass.getSimpleName)

  val maxDebugLength = 200

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

    val dataString =
      if (cut) cutLongData(data)
      else data.toString

    val text =
      if (dataString.isEmpty) ""
      else s": ${dataString}"

    s"${prompt}${text}"
  }

  private def cutLongData(data: Any): String =
    data.toString.take(maxDebugLength)

}
