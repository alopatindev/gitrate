package testing

import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue

import org.scalatest.Matchers._
import org.scalatest.compatible.Assertion
import play.api.libs.json.{JsValue, Json}

import scala.io.Source

trait TestUtils {

  type ConcurrentQueue = ConcurrentLinkedQueue[String]

  implicit class ConcurrentQueueUtils(queue: ConcurrentQueue) {
    import scala.collection.JavaConverters._

    def count(pattern: String): Int =
      queue.iterator.asScala.count { _ contains pattern }
  }

  // org.scalatest.concurrent.TimeLimitedTests isn't flexible enough
  def shouldRunAtMost(limit: Duration)(f: => Unit): Assertion = {
    val beginTime = System.currentTimeMillis()
    f
    val endTime = System.currentTimeMillis()
    val deltaTime: Long = endTime - beginTime
    val tolerance = 3000L
    assert(deltaTime <= (limit.toMillis + tolerance))
  }

  def loadJsonResource(filename: String): JsValue = {
    val text = Source
      .fromFile(s"src/test/resources/$filename")
      .mkString
    Json.parse(text)
  }

}
