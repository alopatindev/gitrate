package controllers

import models.QueryParserModel.{TokenToLexemes, TokenTypes}
import testing.PostgresTestUtils

import org.scalatest.Matchers._
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import org.scalatestplus.play.PlaySpec
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlAction
import play.api.test.Injecting
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class QueryParserSpec extends PlaySpec with GuiceOneAppPerTest with PostgresTestUtils with Injecting {

  "tokenize" should {

    "process empty input" in {
      val parser = inject[QueryParser]
      val lexemes = Seq[String]()
      val expected = TokenToLexemes.empty
      val future = parser.tokenize(lexemes)
      Await.result(future, Duration.Inf) shouldEqual expected
    }

    "detect stop words" in {
      val parser = inject[QueryParser]
      val lexemes = Seq("in")
      val expected = TokenToLexemes.empty + (TokenTypes.stopWord -> Seq("in"))
      val future = parser.tokenize(lexemes)
      Await.result(future, Duration.Inf) shouldEqual expected
    }

    "detect languages" in {
      val parser = inject[QueryParser]
      val lexemes = Seq("javascript", "python")
      val expected = TokenToLexemes.empty + (TokenTypes.language -> Seq("javascript", "python"))
      val future = parser.tokenize(lexemes)
      Await.result(future, Duration.Inf) shouldEqual expected
    }

    "detect technologies" in {
      val parser = inject[QueryParser]
      val lexemes = Seq("nodejs", "playframework")
      val expected = TokenToLexemes.empty + (TokenTypes.technology -> List("nodejs", "playframework"))
      val future = parser.tokenize(lexemes)
      Await.result(future, Duration.Inf) shouldEqual expected
    }

    "detect locations" in {
      val parser = inject[QueryParser]
      val lexemes = Seq("saint", "petersburg", "russian", "federation", "united", "states")
      val expected = TokenToLexemes.empty +
        (TokenTypes.city -> List("Saint Petersburg")) +
        (TokenTypes.country -> List("Russian Federation"))
      val future = parser.tokenize(lexemes)
      Await.result(future, Duration.Inf) shouldEqual expected
    }

  }

  "extractLexemes" should {

    "process empty input" in {
      val parser = inject[QueryParser]
      val expected = Seq.empty
      parser.extractLexemes("") shouldEqual expected
      parser.extractLexemes(" ") shouldEqual expected
    }

    "drop first lexemes for too long queries" in {
      val parser = inject[QueryParser]

      val maxInputLexemes = app.configuration.get[Int]("search.maxInputLexemes")
      val inputTokens = maxInputLexemes + 5
      val lastToken = inputTokens.toString
      val input = (1 to inputTokens).map(_.toString).mkString(" ")

      val result = parser.extractLexemes(input)
      result.length shouldEqual maxInputLexemes
      result.last shouldEqual lastToken
    }

    "extract lexemes" in {
      val parser = inject[QueryParser]
      val input = "c++ developers from saint petersburg"
      val expected = Seq("c++", "developers", "from", "saint", "petersburg")
      parser.extractLexemes(input) shouldEqual expected
    }

    "trim input" in {
      val parser = inject[QueryParser]
      val expected = Seq("c++", "developers")
      parser.extractLexemes("c++ developers ") shouldEqual expected
      parser.extractLexemes("c++ developers   ") shouldEqual expected
      parser.extractLexemes(" c++ developers") shouldEqual expected
    }

    "convert lexemes to lower case" in {
      val parser = inject[QueryParser]
      val input = "C++ Developers"
      val expected = Seq("c++", "developers")
      parser.extractLexemes(input) shouldEqual expected
    }

    "trim prefix and infix whitespace" in {
      val parser = inject[QueryParser]
      val input = "  c++  python linux"
      val expected = Seq("c++", "python", "linux")
      parser.extractLexemes(input) shouldEqual expected
    }

    "ignore punctuation" in {
      val parser = inject[QueryParser]
      val input = """c/c++, js\javascript,,, "hello world" perl. node.js eslint_d"""
      val expected = Seq("c", "c++", "js", "javascript", "hello", "world", "perl", "node.js", "eslint_d")
      parser.extractLexemes(input) shouldEqual expected
    }

  }

  override def initialData: SqlAction[Int, NoStream, Effect] = sqlu"""
    INSERT INTO languages (id, language)
    VALUES
      (DEFAULT, 'JavaScript'),
      (DEFAULT, 'Python'),
      (DEFAULT, 'Scala');

    INSERT INTO technologies (id, language_id, technology, synonym)
    VALUES
      (DEFAULT, (SELECT id FROM languages WHERE language = 'JavaScript'), 'Node.js', DEFAULT),
      (DEFAULT, (SELECT id FROM languages WHERE language = 'JavaScript'), 'nodejs', TRUE),
      (DEFAULT, (SELECT id FROM languages WHERE language = 'Scala'), 'Playframework', DEFAULT)"""

}
