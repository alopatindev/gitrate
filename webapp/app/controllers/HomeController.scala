package controllers

import utils.SlickUtils

import javax.inject.{Inject, Singleton}
import models.TokenTypes
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.mvc.{AbstractController, AnyContent, ControllerComponents, Request}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class HomeController @Inject()(cc: ControllerComponents,
                               configuration: Configuration,
                               val dbConfigProvider: DatabaseConfigProvider,
                               queryParser: QueryParser)
    extends AbstractController(cc)
    with SlickUtils {

  import PostgresDriverPgExt.api._

  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }

  def search(query: String, page: Int) = Action.async { implicit request: Request[AnyContent] =>
    val lexemes: Seq[String] = queryParser.extractLexemes(query)
    queryParser.tokenize(lexemes).flatMap { tokenToLexemes =>
      val languages = tokenToLexemes(TokenTypes.language)
      val technologies = tokenToLexemes(TokenTypes.technology).map(_ ++ ":*")
      val githubLogins = tokenToLexemes(TokenTypes.githubLogin)
      runQuery(
        sql"""
        SELECT
          user_id,
          github_login,
          full_name,
          grades,
          languages,
          technologies
        FROM users_ranks_matview
        WHERE
        (
          CARDINALITY($githubLogins) > 0
          AND github_login_ts @@ TO_TSQUERY(ARRAY_TO_STRING($githubLogins, '|'))
        )
        OR
        (
          (
            CARDINALITY($languages) = 0
            OR languages_ts @@ TO_TSQUERY(ARRAY_TO_STRING($languages, '|'))
          ) AND (
            CARDINALITY($technologies) = 0
            OR technologies_ts @@ TO_TSQUERY(ARRAY_TO_STRING($technologies, '&'))
          )
        )
        ORDER BY
          TS_RANK(github_login_ts, TO_TSQUERY(ARRAY_TO_STRING($githubLogins, '|'))) DESC,
          rank DESC,
          TS_RANK(technologies_ts, TO_TSQUERY(ARRAY_TO_STRING($technologies, '&'))) DESC,
          TS_RANK(languages_ts, TO_TSQUERY(ARRAY_TO_STRING($languages, '|'))) DESC
        LIMIT $maxSearchResultsPerPage
        OFFSET ${(page - 1) * maxSearchResultsPerPage}"""
          .as[(Int, String, String, Seq[String], Seq[String], Seq[String])]
      ).map { users =>
        val results = SearchResults(users.map {
          case (id, githubLogin, fullName, gradesRaw, languages, technologies) =>
            val grades = gradesRaw.map { g =>
              val item = Json.parse(g)
              val category = (item \ "category").as[String]
              val value = (item \ "value").as[Double]
              Grade(category, value)
            }
            val avgGrade = grades.map(_.value).sum / grades.length
            User(
              id = id,
              githubLogin = githubLogin,
              fullName = fullName,
              avgGrade = avgGrade,
              grades = grades,
              languages = languages.map { language =>
                Language(language, verified = true)
              },
              technologies = technologies.map { technology =>
                Technology(technology = technology, verified = true)
              }
            )
        })
        val view = Json.toJson(results)
        Ok(view)
      }
    }
  }

  def suggest(query: String) = Action.async { implicit request: Request[AnyContent] =>
    val lexemes: Seq[String] = queryParser.extractLexemes(query)

    val queries: Future[Vector[String]] = lexemes.lastOption
      .map { incompleteLexeme =>
        val queryPrefix: Seq[String] = lexemes.init
        suggestTokens(queryPrefix, incompleteLexeme)
          .map(_.map(suggestion => (queryPrefix :+ suggestion).mkString(" ")))
      }
      .getOrElse(Future[Vector[String]] { Vector[String]() })

    queries.map { suggestions =>
      val view = Json.obj("suggestions" -> suggestions)
      Ok(view)
    }
  }

  private def suggestTokens(queryPrefix: Seq[String], incompleteLexeme: String): Future[Vector[String]] = {
    val pattern = incompleteLexeme + '%'
    runQuery(sql"""
      SELECT suggestion
      FROM (
        (
          SELECT
            DISTINCT(LOWER(technologies.technology)) AS suggestion,
            (
              (2 + CAST((languages.id IS NOT NULL) AS INTEGER)) * CAST((technologies.synonym = FALSE) AS INTEGER)
            ) AS priority,
            LENGTH(technologies.technology) AS length
          FROM technologies
          LEFT JOIN languages ON languages.id = technologies.language_id AND languages.language ILIKE ANY($queryPrefix)
          WHERE technologies.technology ILIKE $pattern
          LIMIT #$maxSuggestions
        )

        UNION ALL

        (
          SELECT
            DISTINCT(LOWER(languages.language)) AS suggestion,
            2 AS priority,
            LENGTH(languages.language) AS length
          FROM languages
          WHERE languages.language ILIKE $pattern
          LIMIT #$maxSuggestions
        )

        UNION ALL

        (
          SELECT
            DISTINCT(LOWER(cities.city)) AS suggestion,
            1 AS priority,
            LENGTH(cities.city) AS length
          FROM cities
          WHERE cities.city ILIKE $pattern
          LIMIT #$maxSuggestions
        )

        UNION ALL

        (
          SELECT
            DISTINCT(LOWER(countries.country)) AS suggestion,
            1 AS priority,
            LENGTH(countries.country) AS length
          FROM countries
          WHERE countries.country ILIKE $pattern
          LIMIT #$maxSuggestions
        )

        ORDER BY priority DESC, length ASC
        LIMIT #$maxSuggestions
      ) as TMP
      WHERE NOT (suggestion = ANY($queryPrefix))""".as[String])
  }

  case class Grade(category: String, value: Double)
  case class Language(language: String, verified: Boolean)
  case class Technology(technology: String, verified: Boolean)
  case class User(id: Int,
                  githubLogin: String,
                  fullName: String,
                  avgGrade: Double,
                  grades: Seq[Grade],
                  languages: Seq[Language],
                  technologies: Seq[Technology])
  case class SearchResults(users: Seq[User])

  implicit val languageWrites: Writes[Language] = (
    (JsPath \ "language").write[String] and
      (JsPath \ "verified").write[Boolean]
  )(unlift(Language.unapply))

  implicit val technologyWrites: Writes[Technology] = (
    (JsPath \ "technology").write[String] and
      (JsPath \ "verified").write[Boolean]
  )(unlift(Technology.unapply))

  implicit val gradeWrites: Writes[Grade] = (
    (JsPath \ "category").write[String] and
      (JsPath \ "value").write[Double]
  )(unlift(Grade.unapply))

  implicit val userWrites: Writes[User] = (
    (JsPath \ "id").write[Int] and
      (JsPath \ "githubLogin").write[String] and
      (JsPath \ "fullName").write[String] and
      (JsPath \ "avgGrade").write[Double] and
      (JsPath \ "grades").write[Seq[Grade]] and
      (JsPath \ "languages").write[Seq[Language]] and
      (JsPath \ "technologies").write[Seq[Technology]]
  )(unlift(User.unapply))

  implicit val searchResultsWrites = new Writes[SearchResults] {
    def writes(searchResults: SearchResults) = Json.obj(
      "users" -> searchResults.users
    )
  }

  private val maxSearchResultsPerPage = configuration.get[Int]("search.maxResultsPerPage")
  private val maxSuggestions = configuration.get[Int]("search.maxSuggestions")

}
