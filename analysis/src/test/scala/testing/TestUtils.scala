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

package testing

import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import org.scalatest.compatible.Assertion
import org.scalatest.Matchers._
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
    val url = getClass.getResource(filename)
    val text = Source.fromURL(url).mkString
    Json.parse(text)
  }

}
