package utils

import StringUtils._

import org.scalatest.Matchers._
import org.scalatest.WordSpec

class StringUtilsSuite extends WordSpec {

  "StringUtils" can {

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
