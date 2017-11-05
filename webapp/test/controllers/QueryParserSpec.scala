package controllers

import models.{Query, TokenToLexemes, TokenTypes}

import org.scalatest.Matchers._
import org.scalatest.{Outcome, fixture}

class QueryParserSpec extends fixture.WordSpec {

  "parse" should {

    "process empty input" in { fixture =>
      val rawQuery = ""
      val expected = Query(rawQuery = rawQuery, TokenToLexemes.empty)
      fixture.parser.parse(rawQuery) shouldEqual expected
    }

    "detect stop words" in { fixture =>
      val rawQuery = "in"
      val expected = Query(
        rawQuery = rawQuery,
        TokenToLexemes.empty + (TokenTypes.stopWords -> List("in"))
      )
      fixture.parser.parse(rawQuery) shouldEqual expected
    }

    "detect languages" in { fixture =>
      val rawQuery = "javascript python"
      val expected = Query(
        rawQuery = rawQuery,
        TokenToLexemes.empty + (TokenTypes.languages -> List("javascript", "python"))
      )
      fixture.parser.parse(rawQuery) shouldEqual expected
    }

    "detect technologies" in { fixture =>
      val rawQuery = "nodejs playframework"
      val expected = Query(
        rawQuery = rawQuery,
        TokenToLexemes.empty + (TokenTypes.technologies -> List("nodejs", "playframework"))
      )
      fixture.parser.parse(rawQuery) shouldEqual expected
    }

    "detect locations" in { fixture =>
      val rawQuery = "saint petersburg russian federation united states"
      val expected = Query(rawQuery = rawQuery,
                           TokenToLexemes.empty + (TokenTypes.cities -> List("Saint Petersburg"),
                           TokenTypes.countries -> List("Russian Federation")))
      fixture.parser.parse(rawQuery) shouldEqual expected
    }

  }

  "extractLexemes" should {

    "process empty input" in { fixture =>
      {
        val input = ""
        val expected = Seq.empty
        fixture.parser.extractLexemes(input) shouldEqual expected
      }
      {
        val input = " "
        val expected = Seq.empty
        fixture.parser.extractLexemes(input) shouldEqual expected
      }
    }

    "extract lexemes" in { fixture =>
      val input = "c++ developers from saint petersburg"
      val expected = Seq("c++", "developers", "from", "saint", "petersburg")
      fixture.parser.extractLexemes(input) shouldEqual expected
    }

    "add empty lexeme if non-empty input ends with delimiter(s)" in { fixture =>
      {
        val input = "c++ developers "
        val expected = Seq("c++", "developers", "")
        fixture.parser.extractLexemes(input) shouldEqual expected
      }
      {
        val input = "c++ developers   "
        val expected = Seq("c++", "developers", "")
        fixture.parser.extractLexemes(input) shouldEqual expected
      }
    }

    "convert lexemes to lower case" in { fixture =>
      val input = "C++ Developers"
      val expected = Seq("c++", "developers")
      fixture.parser.extractLexemes(input) shouldEqual expected
    }

    "trim prefix and infix whitespace" in { fixture =>
      val input = "  c++  python linux"
      val expected = Seq("c++", "python", "linux")
      fixture.parser.extractLexemes(input) shouldEqual expected
    }

    "ignore punctuation" in { fixture =>
      val input = """c/c++, js\javascript,,, "hello world" perl. node.js eslint_d"""
      val expected = Seq("c", "c++", "js", "javascript", "hello", "world", "perl", "node.js", "eslint_d")
      fixture.parser.extractLexemes(input) shouldEqual expected
    }

  }

  case class FixtureParam(parser: QueryParser)

  def withFixture(test: OneArgTest): Outcome = {
    def fakeIsStopWord(lexeme: String): Boolean = Set("in", "or") contains lexeme
    def fakeIsLanguage(lexeme: String): Boolean = Set("javascript", "python") contains lexeme
    def fakeIsTechnology(lexeme: String): Boolean = Set("nodejs", "playframework") contains lexeme

    val fakePredicates = Map(
      TokenTypes.languages -> fakeIsLanguage _,
      TokenTypes.technologies -> fakeIsTechnology _,
      TokenTypes.stopWords -> fakeIsStopWord _
    )

    val parser = new QueryParser(fakePredicates)
    val theFixture = FixtureParam(parser)
    withFixture(test.toNoArgTest(theFixture))
  }

}
