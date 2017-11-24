package controllers

import models.{TokenToLexemes, TokenTypes}

import org.scalatest.TestData
import org.scalatest.Matchers._
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.db.DBApi
import play.api.db.evolutions.Evolutions
import play.api.test.Injecting
import play.api.db.slick.DatabaseConfigProvider
import play.api.Application
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.language.implicitConversions
import slick.jdbc.JdbcProfile

class QueryParserSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {

  implicit override def newAppForTest(testData: TestData): Application = {
    val slickDatabase = "default"
    val user = "gitrate_test"
    val database = user
    val databaseKeyPrefix = s"slick.dbs.$slickDatabase.db.properties"
    val app = new GuiceApplicationBuilder()
      .configure(Map(s"$databaseKeyPrefix.user" -> user, s"$databaseKeyPrefix.url" -> s"jdbc:postgresql:$database"))
      .build()

    val databaseApi = app.injector.instanceOf[DBApi]
    val defaultDb = databaseApi.database(slickDatabase)
    Evolutions.cleanupEvolutions(defaultDb)
    Evolutions.applyEvolutions(defaultDb)

    val dbConfigProvider = app.injector.instanceOf[DatabaseConfigProvider]
    val dbConfig = dbConfigProvider.get[JdbcProfile]
    import dbConfig._
    import profile.api._

    val result = db.run(sqlu"""
      INSERT INTO languages (id, language)
      VALUES
        (DEFAULT, 'JavaScript'),
        (DEFAULT, 'Python'),
        (DEFAULT, 'Scala');

      INSERT INTO technologies (id, language_id, technology, synonym)
      VALUES
        (DEFAULT, (SELECT id FROM languages WHERE language = 'JavaScript'), 'Node.js', DEFAULT),
        (DEFAULT, (SELECT id FROM languages WHERE language = 'JavaScript'), 'nodejs', TRUE),
        (DEFAULT, (SELECT id FROM languages WHERE language = 'Scala'), 'Playframework', DEFAULT);

      INSERT INTO stop_words (id, word)
      VALUES (DEFAULT, 'in')""".transactionally)
    Await.result(result, Duration.Inf)

    app
  }

  "parse" should {

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

}
