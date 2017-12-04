package controllers

import models.SearcherModel._
import models.QueryParserModel.{Lexemes, TokenToLexemes, TokenTypes}
import utils.SlickUtils

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class Searcher @Inject()(configuration: Configuration,
                         val dbConfigProvider: DatabaseConfigProvider,
                         queryParser: QueryParser)
    extends SlickUtils {

  import PostgresDriverPgExt.api._

  def search(query: String, page: Int): Future[JsValue] =
    computeSearchResults(query, page)
      .map(results => Json.toJson(results))

  private def computeSearchResults(query: String, page: Int): Future[SearchResults] = {
    val lexemes: Lexemes = queryParser.extractLexemes(query)
    queryParser
      .tokenize(lexemes)
      .flatMap { tokenToLexemes =>
        requestRawUsers(tokenToLexemes, page)
          .map(rawUsers => SearchResults(rawUsers.map(toUser)))
      }
  }

  private def toUser(rawUser: RawUser) = rawUser match {
    case (id, githubLogin, fullName, gradesRaw, languagesRaw, technologiesRaw) =>
      val grades = gradesRaw.map { gradeRaw =>
        val item = Json.parse(gradeRaw)
        val category = (item \ "category").as[String]
        val value = (item \ "value").as[Double]
        Grade(category, value)
      }
      val languages = languagesRaw.map { language =>
        Language(language, verified = true)
      }
      val technologies = technologiesRaw.map { technology =>
        Technology(technology = technology, verified = true)
      }
      User(
        id = id,
        githubLogin = githubLogin,
        fullName = fullName,
        avgGrade = grades.map(_.value).sum / grades.length,
        grades = grades,
        languages = languages,
        technologies = technologies
      )
  }

  private def requestRawUsers(tokenToLexemes: TokenToLexemes, page: Int) = {
    val githubLogins = tokenToLexemes(TokenTypes.githubLogin)
    val languages = tokenToLexemes(TokenTypes.language)
    val technologies = tokenToLexemes(TokenTypes.technology).map(_ ++ ":*")
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
        OFFSET ${(page - 1) * maxSearchResultsPerPage}""".as[RawUser]
    )
  }

  private val maxSearchResultsPerPage = configuration.get[Int]("search.maxResultsPerPage")

}
