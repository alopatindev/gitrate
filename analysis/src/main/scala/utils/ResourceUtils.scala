package gitrate.utils

import java.io.InputStream

import scala.io.Source

trait ResourceUtils {

  def resourceToString(resourceFilePath: String): String = {
    val stream = getClass.getResourceAsStream(resourceFilePath)
    inputStreamToString(stream)
  }

  private def inputStreamToString(stream: InputStream): String =
    Source
      .fromInputStream(stream)
      .mkString

}
