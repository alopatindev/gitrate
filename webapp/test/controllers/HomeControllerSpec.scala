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

  "HomeController GET /search?q=..." should {

    "return search results" in {
      val request = FakeRequest(GET, "/search?q=javascript&page=1")
      val response = route(app, request).get
      validateResponse(response)

      val body = Json.parse(contentAsString(response))
      val users: Seq[JsValue] = body \ "users" match {
        case JsDefined(JsArray(users: Seq[JsValue])) => users
        case _ =>
          assert(false)
          Seq()
      }

      val head = users.head
      (head \ "id").as[Int] shouldEqual 2
      (head \ "githubLogin").as[String] shouldEqual "userB"

      val technologies = for {
        technology <- (head \ "technologies").as[Seq[JsValue]]
      } yield (technology \ "technology").as[String]

      technologies should contain("jboss")
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
      val maxSuggestions = app.configuration.get[Int]("search.maxSuggestions")

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
      (DEFAULT, (SELECT id FROM languages WHERE language = 'JavaScript'), 's-foo-bar-11', FALSE),
      (DEFAULT, (SELECT id FROM languages WHERE language = 'Python'), 'pygtk', FALSE);

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
      (DEFAULT, 789, 'userC', 'C C', DEFAULT);

    INSERT INTO developers (
      id,
      user_id,
      show_email,
      job_seeker,
      available_for_relocation,
      programming_experience_months,
      work_experience_months,
      description,
      raw_location,
      country_id,
      city_id,
      viewed
    ) VALUES (
      DEFAULT,
      (SELECT id FROM users WHERE github_login = 'userA'),
      DEFAULT,
      FALSE,
      DEFAULT,
      DEFAULT,
      DEFAULT,
      DEFAULT,
      DEFAULT,
      DEFAULT,
      DEFAULT,
      DEFAULT
    ), (
      DEFAULT,
      (SELECT id FROM users WHERE github_login = 'userB'),
      DEFAULT,
      FALSE,
      DEFAULT,
      DEFAULT,
      DEFAULT,
      DEFAULT,
      DEFAULT,
      DEFAULT,
      DEFAULT,
      DEFAULT
    );

    INSERT INTO technologies_users (id, technology_id, user_id)
    VALUES (
      DEFAULT,
      (SELECT id from technologies WHERE technology = 'jade'),
      (SELECT id FROM users WHERE github_login = 'userA')
    ), (
      DEFAULT,
      (SELECT id from technologies WHERE technology = 'jboss'),
      (SELECT id FROM users WHERE github_login = 'userB')
    ), (
      DEFAULT,
      (SELECT id from technologies WHERE technology = 's-foo-bar-1'),
      (SELECT id FROM users WHERE github_login = 'userB')
    ), (
      DEFAULT,
      (SELECT id from technologies WHERE technology = 'pygtk'),
      (SELECT id FROM users WHERE github_login = 'userB')
    ), (
      DEFAULT,
      (SELECT id from technologies WHERE technology = 's-foo-bar-2'),
      (SELECT id FROM users WHERE github_login = 'userB')
    );

    INSERT INTO technologies_users_settings (id, technologies_users_id, verified)
    VALUES
      (DEFAULT, 1, TRUE),
      (DEFAULT, 2, TRUE),
      (DEFAULT, 3, TRUE),
      (DEFAULT, 4, FALSE),
      (DEFAULT, 5, FALSE);

    INSERT INTO repositories (id, raw_id, user_id, name, lines_of_code, updated_by_analyzer)
    VALUES (
      DEFAULT,
      'foo_id',
      (SELECT id FROM users WHERE github_login = 'userA'),
      'bar',
      12345,
      DEFAULT
    ), (
      DEFAULT,
      'hello_id',
      (SELECT id FROM users WHERE github_login = 'userB'),
      'hello_world',
      777,
      DEFAULT
    );

    INSERT INTO grade_categories (id, category)
    VALUES
      (DEFAULT, 'Secure'),
      (DEFAULT, 'Testable');

    INSERT INTO grades (id, category_id, value, repository_id)
    VALUES (
      DEFAULT,
      (SELECT id FROM grade_categories WHERE category = 'Secure'),
      0.8,
      (SELECT id FROM repositories WHERE raw_id = 'foo_id')
    ), (
      DEFAULT,
      (SELECT id FROM grade_categories WHERE category = 'Testable'),
      0.5,
      (SELECT id FROM repositories WHERE raw_id = 'hello_id')
    ), (
      DEFAULT,
      (SELECT id FROM grade_categories WHERE category = 'Secure'),
      1.0,
      (SELECT id FROM repositories WHERE raw_id = 'hello_id')
    );

    REFRESH MATERIALIZED VIEW users_ranks_matview"""

}
