package controllers

import testing.PostgresTestUtils

import org.scalatest.Matchers._
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import play.api.test._
import play.api.test.Helpers._
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlAction

class HomeControllerSpec extends PlaySpec with GuiceOneAppPerTest with PostgresTestUtils with Injecting {

  "HomeController GET /" should {

    "render the index page from the application" ignore {
      val controller = inject[HomeController]
      val request = FakeRequest(GET, "/")
      val result = controller.index().apply(request)

      status(result) mustBe OK
      charset(result) mustBe Some("utf-8")
      contentType(result) mustBe Some("text/html")
      contentAsString(result) must include("Welcome to Play")
    }

    "render the index page from the router" ignore {
      val request = FakeRequest(GET, "/")
      val result = route(app, request).get

      status(result) mustBe OK
      charset(result) mustBe Some("utf-8")
      contentType(result) mustBe Some("text/html")
      contentAsString(result) must include("Welcome to Play")
    }
  }

  "HomeController GET /search?query=..." ignore {

    "return search results" in {
      val request = FakeRequest(GET, "/search?q=javascript")
      val result = route(app, request).get

      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      contentAsString(result) must include("users")
    }

    "support pagination" in {
      val request = FakeRequest(GET, "/search?q=javascript&page=2")
      val result = route(app, request).get

      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      contentAsString(result) must include("users")
    }
  }

  "HomeController GET /suggest?q=..." should {

    "suggest search queries with valid priority: technologies of the language, technologies, languages, cities and countries" in {
      val request = FakeRequest(GET, "/suggest?q=java%20j")
      val result = route(app, request).get

      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")

      val expected = Seq("java jaafoobar", "java jboss", "java jade", "java javascript", "java joetsu", "java japan")

      val body = Json.parse(contentAsString(result))
      (body \ "suggestions") match {
        case JsDefined(JsArray(queries: Seq[JsValue])) =>
          val results = queries.map(_.as[String])
          results shouldEqual expected
        case _ => assert(false)
      }
    }

  }

  override def initialData: SqlAction[Int, NoStream, Effect] = sqlu"""
    INSERT INTO languages
    VALUES
      (DEFAULT, 'JavaScript'),
      (DEFAULT, 'Java'),
      (DEFAULT, 'C'),
      (DEFAULT, 'C++'),
      (DEFAULT, 'Python');

    INSERT INTO technologies
    VALUES
      (DEFAULT, (SELECT id FROM languages WHERE language = 'Java'), 'jaafoobar'),
      (DEFAULT, (SELECT id FROM languages WHERE language = 'Java'), 'jboss'),
      (DEFAULT, (SELECT id FROM languages WHERE language = 'JavaScript'), 'jade');

    INSERT INTO cities
    VALUES
      (DEFAULT, 'Saint Petersburg'),
      (DEFAULT, 'Moscow'),
      (DEFAULT, 'Joetsu');

    INSERT INTO countries
    VALUES
      (DEFAULT, 'Russian Federation'),
      (DEFAULT, 'Japan')"""

}
