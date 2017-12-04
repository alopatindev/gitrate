package controllers

import common.LocationParser
import models.QueryParserModel.{Lexemes, TokenToLexemes, TokenTypes}
import utils.SlickUtils

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import slick.jdbc.PostgresProfile.api._

@Singleton
class QueryParser @Inject()(configuration: Configuration, val dbConfigProvider: DatabaseConfigProvider)
    extends SlickUtils {

  import PostgresDriverPgExt.api._

  private[controllers] def tokenize(lexemes: Lexemes): Future[TokenToLexemes] = {
    type T = (String, String)

    val query = sql"""
      SELECT 'stopWord' AS token_type, LOWER(word) AS lexeme
      FROM UNNEST($lexemes) AS word
      WHERE CARDINALITY(TS_LEXIZE('english_stem', word)) = 0

      UNION ALL

      SELECT 'language' AS token_type, LOWER(language) AS lexeme
      FROM languages
      WHERE language ILIKE ANY($lexemes)

      UNION ALL

      SELECT 'technology' AS token_type, LOWER(technology) AS lexeme
      FROM technologies
      WHERE technology ILIKE ANY($lexemes)

      UNION ALL

      SELECT 'githubLogin' AS token_type, LOWER(github_login) AS lexeme
      FROM users
      WHERE github_login ILIKE ANY($lexemes)""".as[T]

    runQuery(query)
      .map(_.groupBy { case (token, _) => token }.mapValues(_.map { case (_, lexeme) => lexeme }))
      .map {
        case tokenToLexemes: TokenToLexemes =>
          val detectedLexemes: Set[String] = tokenToLexemes.values.flatten.toSet
          val unknownLexemes: Seq[String] = lexemes.filterNot(detectedLexemes.contains)
          val rawUnknown: String = unknownLexemes.mkString(" ")
          val location = LocationParser.parse(rawUnknown)
          val city = TokenTypes.city -> location.city.toList
          val country = TokenTypes.country -> location.country.toList
          TokenToLexemes.empty ++ (tokenToLexemes + city + country)
      }
  }

  private[controllers] def extractLexemes(rawQuery: String): Lexemes =
    delimiterPattern
      .split(rawQuery.toLowerCase)
      .takeRight(maxInputLexemes)
      .toSeq
      .flatMap {
        case lexemePattern(_, lexeme, _) => Some(lexeme)
        case _                           => None
      }

  private[this] val lexemePattern = """^([.,;'"]*)(.+?)([.,;'"]*)$""".r
  private[this] val delimiterPattern = """([\s/\\]+)""".r
  private[this] val maxInputLexemes = configuration.get[Int]("search.maxInputLexemes")

}
