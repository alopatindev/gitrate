package hiregooddevs.utils

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalatest.Matchers._
import org.scalatest.prop.Checkers

@RunWith(classOf[JUnitRunner])
class StringUtilsSuite extends FunSuite with Checkers {

  import StringUtils._

  test("formatTemplate should replace placeholders with text") {
    "".formatTemplate(Map()) shouldEqual ""
    "Hello".formatTemplate(Map()) shouldEqual "Hello"
    "Hello".formatTemplate(Map("unused" -> "value")) shouldEqual "Hello"
    "Hello ${key}!".formatTemplate(Map("key" -> "World")) shouldEqual "Hello World!"
    "Hello ${key}!".formatTemplate(Map("key" -> "World", "unused" -> "value")) shouldEqual "Hello World!"
  }

}
