package gitrate.utils

import java.util.concurrent.ConcurrentLinkedQueue
import play.api.libs.json.{Json, JsValue}
import scala.io.Source

trait TestUtils {

  type ConcurrentQueue = ConcurrentLinkedQueue[String]

  implicit class ConcurrentQueueUtils(queue: ConcurrentQueue) {
    import scala.collection.JavaConverters._

    def count(pattern: String): Int =
      queue.iterator.asScala.count { _ contains pattern }
  }

  def loadJsonResource(filename: String): JsValue = {
    val text = Source
      .fromFile(s"src/test/resources/$filename")
      .mkString
    Json.parse(text)
  }

}
