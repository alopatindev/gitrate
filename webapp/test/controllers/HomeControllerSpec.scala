package controllers

import testing.PostgresTestUtils

import org.scalatest.compatible.Assertion
import org.scalatest.Matchers._
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{Json, JsArray, JsDefined, JsValue}
import play.api.mvc.Result
import play.api.test.{FakeRequest, Injecting}
import play.api.test.Helpers._
import scala.concurrent.Future
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlAction

class HomeControllerSpec extends PlaySpec with GuiceOneAppPerTest with PostgresTestUtils with Injecting {

  "HomeController GET /" should {

    "render the index page" in {
      val request = FakeRequest(GET, "/")
      val response = route(app, request).get
      validateResponse(response, expectedContentType = "text/html")
      contentAsString(response) must include("Welcome to Play")
    }
  }

  "HomeController GET /search?q=..." ignore {

    "return search results" in {
      val request = FakeRequest(GET, "/search?q=javascript")
      val response = route(app, request).get
      validateResponse(response)
      contentAsString(response) must include("users")
    }

    "support pagination" in {
      val request = FakeRequest(GET, "/search?q=javascript&page=2")
      val response = route(app, request).get
      validateResponse(response)
      contentAsString(response) must include("users")
    }
  }

  "HomeController GET /suggest?q=..." should {

    "suggest search queries" in {
      val request = FakeRequest(GET, "/suggest?q=j")
      val response = route(app, request).get
      validateResponse(response)
      assert(extractSuggestions(response) contains "java")
    }

    "suggest search queries with valid priority and order" in {
      // priorities:
      // 3 (highest): languages, technologies of the language
      // 2: technologies, languages
      // 1: cities, countries
      // 0: technology synonyms
      // results are grouped by priority, each group is sorted by length (ascending)

      val request = FakeRequest(GET, "/suggest?q=java%20j")
      val response = route(app, request).get
      validateResponse(response)

      val expected =
        Seq("java jni",
            "java jboss",
            "java jaafoobar",
            "java jade",
            "java javascript",
            "java japan",
            "java joetsu",
            "java jboss-foobar")

      extractSuggestions(response) shouldEqual expected
    }

    "limit number of results" in {
      val controller = inject[HomeController]
      val input = "s"
      val response = controller.suggest(input).apply(FakeRequest(GET, s"/suggest?q=$input"))
      val maxSuggestions = app.configuration.get[Int]("searchQuery.maxSuggestions")

      validateResponse(response)
      extractSuggestions(response).length shouldEqual maxSuggestions
    }

    def extractSuggestions(response: Future[Result]): Seq[String] = {
      val body = Json.parse(contentAsString(response))
      (body \ "suggestions") match {
        case JsDefined(JsArray(queries: Seq[JsValue])) =>
          queries.map(_.as[String])
        case _ =>
          assert(false)
          Seq()
      }
    }

  }

  private def validateResponse(response: Future[Result],
                               expectedContentType: String = "application/json",
                               expectedStatus: Int = OK): Assertion = {
    charset(response).foreach(_ shouldEqual "utf-8")
    contentType(response) mustBe Some(expectedContentType)
    status(response) mustBe expectedStatus
  }

  override def initialData: SqlAction[Int, NoStream, Effect] = sqlu"""
    INSERT INTO languages (id, language)
    VALUES
      (DEFAULT, 'JavaScript'),
      (DEFAULT, 'Java'),
      (DEFAULT, 'C'),
      (DEFAULT, 'C++'),
      (DEFAULT, 'Python');

    INSERT INTO technologies (id, language_id, technology, synonym)
    VALUES
      (DEFAULT, (SELECT id FROM languages WHERE language = 'Java'), 'jaafoobar', FALSE),
      (DEFAULT, (SELECT id FROM languages WHERE language = 'Java'), 'jni', FALSE),
      (DEFAULT, (SELECT id FROM languages WHERE language = 'Java'), 'jboss', FALSE),
      (DEFAULT, (SELECT id FROM languages WHERE language = 'Java'), 'jboss-foobar', TRUE),
      (DEFAULT, (SELECT id FROM languages WHERE language = 'JavaScript'), 'jade', FALSE),
      (DEFAULT, (SELECT id FROM languages WHERE language = 'JavaScript'), 's-foo-bar-1', FALSE),
      (DEFAULT, (SELECT id FROM languages WHERE language = 'JavaScript'), 's-foo-bar-2', FALSE),
      (DEFAULT, (SELECT id FROM languages WHERE language = 'JavaScript'), 's-foo-bar-3', FALSE),
      (DEFAULT, (SELECT id FROM languages WHERE language = 'JavaScript'), 's-foo-bar-4', FALSE),
      (DEFAULT, (SELECT id FROM languages WHERE language = 'JavaScript'), 's-foo-bar-5', FALSE),
      (DEFAULT, (SELECT id FROM languages WHERE language = 'JavaScript'), 's-foo-bar-6', FALSE),
      (DEFAULT, (SELECT id FROM languages WHERE language = 'JavaScript'), 's-foo-bar-7', FALSE),
      (DEFAULT, (SELECT id FROM languages WHERE language = 'JavaScript'), 's-foo-bar-8', FALSE),
      (DEFAULT, (SELECT id FROM languages WHERE language = 'JavaScript'), 's-foo-bar-9', FALSE),
      (DEFAULT, (SELECT id FROM languages WHERE language = 'JavaScript'), 's-foo-bar-10', FALSE),
      (DEFAULT, (SELECT id FROM languages WHERE language = 'JavaScript'), 's-foo-bar-11', FALSE);

    INSERT INTO cities (id, city)
    VALUES
      (DEFAULT, 'Saint Petersburg'),
      (DEFAULT, 'Moscow'),
      (DEFAULT, 'Joetsu');

    INSERT INTO countries (id, country)
    VALUES
      (DEFAULT, 'Russian Federation'),
      (DEFAULT, 'Japan');

    INSERT INTO users (id, github_user_id, github_login, full_name, updated_by_user)
    VALUES
      (DEFAULT, 123, 'userA', 'A A', DEFAULT),
      (DEFAULT, 456, 'userB', 'B B', DEFAULT),
      (DEFAULT, 789, 'userC', 'C C', DEFAULT)"""

}
