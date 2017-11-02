package common

import LocationParser._

import org.scalatest.Matchers._
import org.scalatest.WordSpec

class LocationParserSuite extends WordSpec {

  "parse" should {

    "process empty input" in {
      assert(parse("") === Location(country = None, city = None))
    }

    "parse geo locations" in {
      assert(
        parse("Saint Petersburg, Russia") === Location(country = Some("Russian Federation"),
                                                       city = Some("Saint Petersburg")))
      assert(parse("Russia") === Location(country = Some("Russian Federation"), city = None))
      assert(parse("CA") === Location(country = Some("United States"), city = None))
    }

    "process only one location" in {
      val input = "united states saint petersburg russian federation"
      assert(parse(input) === Location(country = Some("Russian Federation"), city = Some("Saint Petersburg")))
    }

  }

}
