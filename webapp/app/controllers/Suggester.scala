package controllers

import utils.SlickUtils

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.{Json, JsValue}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class Suggester @Inject()(configuration: Configuration,
                          val dbConfigProvider: DatabaseConfigProvider,
                          queryParser: QueryParser)
    extends SlickUtils {

  import PostgresDriverPgExt.api._

  def suggest(query: String): Future[JsValue] =
    computeSuggestions(query)
      .map(suggestions => Json.obj("suggestions" -> suggestions))

  private def computeSuggestions(query: String): Future[Seq[String]] = {
    val lexemes: Seq[String] = queryParser.extractLexemes(query)

    lexemes.lastOption
      .map { incompleteLexeme =>
        val queryPrefix: Seq[String] = lexemes.init
        requestTokens(queryPrefix, incompleteLexeme)
          .map(_.map(suggestion => (queryPrefix :+ suggestion).mkString(" ")))
      }
      .getOrElse(Future[Seq[String]] { Seq[String]() })
  }

  private def requestTokens(queryPrefix: Seq[String], incompleteLexeme: String): Future[Seq[String]] = {
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
        ) UNION ALL (
          SELECT
            DISTINCT(LOWER(languages.language)) AS suggestion,
            2 AS priority,
            LENGTH(languages.language) AS length
          FROM languages
          WHERE languages.language ILIKE $pattern
          LIMIT #$maxSuggestions
        ) UNION ALL (
          SELECT
            DISTINCT(LOWER(cities.city)) AS suggestion,
            1 AS priority,
            LENGTH(cities.city) AS length
          FROM cities
          WHERE cities.city ILIKE $pattern
          LIMIT #$maxSuggestions
        ) UNION ALL (
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

  private val maxSuggestions = configuration.get[Int]("search.maxSuggestions")

}
