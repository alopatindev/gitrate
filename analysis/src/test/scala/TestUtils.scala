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

  implicit class SeqUtils[T](xs: Seq[T]) {
    def hasDuplicates = xs.groupBy(i => i).mapValues(_.length).forall {
      case (_, count) => count > 1
    }

    def hasOnlyUniqueItems = !hasDuplicates
  }

  def loadJsonResource(filename: String): JsValue = {
    val text = Source
      .fromFile(s"src/test/resources/$filename")
      .mkString
    Json.parse(text)
  }

}
