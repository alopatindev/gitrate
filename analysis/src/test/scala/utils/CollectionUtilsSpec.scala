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

import CollectionUtils._

import org.scalatest.Matchers._
import org.scalatest.WordSpec

class CollectionUtilsSpec extends WordSpec {

  "seqOfMapsToMap" should {

    "process empty input" in {
      assert(seqOfMapsToMapOfSeq(Seq.empty).isEmpty)
    }

    "merge values of common keys" in {
      type T = MapOfSeq[String, String]
      val map1: T = Map("key1" -> Seq("value1", "value2"), "key2" -> Seq("value3", "value4"))
      val map2: T = Map("key1" -> Seq("value3"), "key3" -> Seq.empty)
      val input: Seq[T] = Seq(map1, map2)

      val expected: T =
        Map("key1" -> Seq("value1", "value2", "value3"), "key2" -> Seq("value3", "value4"), "key3" -> Seq.empty)
      seqOfMapsToMapOfSeq(input) shouldEqual expected
    }

  }

}
