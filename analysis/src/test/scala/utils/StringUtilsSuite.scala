package utils

import org.scalatest.Matchers._
import org.scalatest.WordSpec

class StringUtilsSuite extends WordSpec {

  import StringUtils._

  "StringUtils" can {

    "stem" should {

      "build tree of stems to synonyms" in {
        val input = Seq("programming", "variance", "invariant", "variant", "variants", "prog", "metal")
        val result = stem(input, minLength = 2, limit = 10)
        val expected = Map("prog" -> Set("programming"),
                           "variant" -> Set("invariant", "variants"),
                           "variance" -> Set(),
                           "metal" -> Set())
        result shouldEqual expected
      }

      "ignore too short words" in {
        val input = Seq("programming", "variance", "invariant", "variant", "variants", "prog", "metal")
        val result = stem(input, minLength = 5, limit = 10)
        val expected = Map("variant" -> Set("invariant", "variants"),
                           "variance" -> Set(),
                           "prog" -> Set(),
                           "programming" -> Set(),
                           "metal" -> Set())
        result shouldEqual expected
      }

      "ignore items when out of limit" in {
        val input = Seq("programming", "variance", "invariant", "variant", "variants", "prog", "metal")
        val result = stem(input, minLength = 2, limit = 4)
        val expected = Map("programming" -> Set(),
                           "variant" -> Set("invariant"),
                           "variants" -> Set(),
                           "variance" -> Set(),
                           "prog" -> Set(),
                           "metal" -> Set())
        result shouldEqual expected
      }

    }

    "formatTemplate" should {

      "left string unchanged when no placeholders" in {
        "".formatTemplate(Map()) shouldEqual ""
        "Hello".formatTemplate(Map()) shouldEqual "Hello"
        "Hello".formatTemplate(Map("unused" -> "value")) shouldEqual "Hello"
        ("Hello $" + "{key}!").formatTemplate(Map()) shouldEqual ("Hello $" + "{key}!")
      }

      "replace placeholders with text" in {
        ("Hello $" + "{key}!").formatTemplate(Map("key" -> "World")) shouldEqual "Hello World!"
        ("Hello $" + "{key}!").formatTemplate(Map("key" -> "World", "unused" -> "value")) shouldEqual "Hello World!"
        ("Hello $" + "{key}$" + "{anotherKey}")
          .formatTemplate(Map("key" -> "World", "anotherKey" -> "!")) shouldEqual "Hello World!"
      }

    }

  }

}
