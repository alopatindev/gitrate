package controllers

import org.scalatest.Matchers._
import org.scalatest.{Outcome, fixture}

class QueryParserSpec extends fixture.WordSpec {

  "parseQuery" should {

    "process empty input" in { fixture =>
      val rawQuery = ""
      val expected = Query(rawQuery = rawQuery, Query.emptyItems)
      fixture.parser.parseQuery(rawQuery) shouldEqual expected
    }

    "detect stop words" in { fixture =>
      val rawQuery = "in"
      val expected = Query(
        rawQuery = rawQuery,
        Query.emptyItems + (Query.keys.stopWords -> List("in"))
      )
      fixture.parser.parseQuery(rawQuery) shouldEqual expected
    }

    "detect languages" in { fixture =>
      val rawQuery = "javascript python"
      val expected = Query(
        rawQuery = rawQuery,
        Query.emptyItems + (Query.keys.languages -> List("javascript", "python"))
      )
      fixture.parser.parseQuery(rawQuery) shouldEqual expected
    }

    "detect technologies" in { fixture =>
      val rawQuery = "nodejs playframework"
      val expected = Query(
        rawQuery = rawQuery,
        Query.emptyItems + (Query.keys.technologies -> List("nodejs", "playframework"))
      )
      fixture.parser.parseQuery(rawQuery) shouldEqual expected
    }

    "detect locations" in { fixture =>
      val rawQuery = "saint petersburg russian federation united states"
      val expected = Query(
        rawQuery = rawQuery,
        Query.emptyItems + (Query.keys.cities -> List("Saint Petersburg"),
        Query.keys.countries -> List("Russian Federation")
      ))
      fixture.parser.parseQuery(rawQuery) shouldEqual expected
    }

  }

  "tokenize" should {

    "process empty input" in { fixture =>
      val input = ""
      val expected = Seq.empty
      fixture.parser.tokenize(input) shouldEqual expected
    }

    "split string into tokens" in { fixture =>
      val input = "c++ developers from saint petersburg"
      val expected = Seq("c++", "developers", "from", "saint", "petersburg")
      fixture.parser.tokenize(input) shouldEqual expected
    }

    "trim whitespace" in { fixture =>
      val input = " c++  python linux  "
      val expected = Seq("c++", "python", "linux")
      fixture.parser.tokenize(input) shouldEqual expected
    }

    "ignore punctuation" in { fixture =>
      val input = """c/c++, js\javascript,,, "hello world" perl. node.js"""
      val expected = Seq("c", "c++", "js", "javascript", "hello", "world", "perl", "node.js")
      fixture.parser.tokenize(input) shouldEqual expected
    }

  }

  case class FixtureParam(parser: QueryParser)

  def withFixture(test: OneArgTest): Outcome = {
    def fakeIsStopWord(token: String): Boolean = Seq("in", "or") contains token
    def fakeIsLanguage(token: String): Boolean = Seq("javascript", "python") contains token
    def fakeIsTechnology(token: String): Boolean = Seq("nodejs", "playframework") contains token
    def fakeIsCity(token: String): Boolean = Seq() contains token
    def fakeIsCountry(token: String): Boolean = Seq() contains token
    def fakeIsUnknown(token: String): Boolean = true

    val fakePredicates = Map(
      Query.keys.languages -> fakeIsLanguage _,
      Query.keys.technologies -> fakeIsTechnology _,
      Query.keys.cities -> fakeIsCity _,
      Query.keys.countries -> fakeIsCountry _,
      Query.keys.stopWords -> fakeIsStopWord _,
      Query.keys.unknown -> fakeIsUnknown _
    )

    val parser = new QueryParser(fakePredicates)
    val theFixture = FixtureParam(parser)
    withFixture(test.toNoArgTest(theFixture))
  }

}
