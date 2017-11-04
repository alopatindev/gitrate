package utils

import CollectionUtils._

import org.scalatest.Matchers._
import org.scalatest.WordSpec

class CollectionUtilsSuite extends WordSpec {

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
