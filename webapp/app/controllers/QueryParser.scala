package controllers

import common.LocationParser
import models.{Lexemes, Predicate, Query, TokenToLexemes, TokenTypes}

import scala.annotation.tailrec

class QueryParser(predicates: Map[String, Predicate]) {

  def parse(rawQuery: String): Query = {
    @tailrec
    def tokenize(lexemes: Lexemes, tokenToLexemes: TokenToLexemes): TokenToLexemes = lexemes match {
      case lexeme :: tail if lexeme.nonEmpty =>
        val key = TokenTypes.parsableWithPredicates
          .filter(allPredicates(_)(lexeme))
          .head
        val value: Lexemes = tokenToLexemes(key) :+ lexeme
        val newTokenToLexemes = tokenToLexemes + (key -> value)
        tokenize(tail, newTokenToLexemes)
      case _ => tokenToLexemes
    }

    val tokenToLexemes = tokenize(extractLexemes(rawQuery), TokenToLexemes.empty)

    val rawUnknown: String = tokenToLexemes(TokenTypes.unknown).mkString(" ")
    val location = LocationParser.parse(rawUnknown)
    val cities = TokenTypes.cities -> location.city.map(List(_)).getOrElse(List())
    val countries = TokenTypes.countries -> location.country.map(List(_)).getOrElse(List())

    val newTokenToLexemes = tokenToLexemes + cities + countries + (TokenTypes.unknown -> List())
    Query(rawQuery, newTokenToLexemes)
  }

  def extractLexemes(rawQuery: String): Lexemes = {
    val prefixTokens = delimiterPattern
      .split(rawQuery.toLowerCase)
      .toList
      .flatMap {
        case lexemePattern(_, lexeme, _) => Some(lexeme)
        case _                           => None
      }

    val postfixTokens = rawQuery.lastOption.filter(_ => prefixTokens.nonEmpty) match {
      case Some(delimiterPattern(_)) => List("")
      case _                         => Nil
    }

    prefixTokens ++ postfixTokens
  }

  private def isUnknown(lexeme: String): Boolean = true
  private[this] val allPredicates = predicates ++ Map(TokenTypes.unknown -> isUnknown _)

  private[this] val lexemePattern = """^([.,;'"]*)(.+?)([.,;'"]*)$""".r
  private[this] val delimiterPattern = """([\s/\\]+)""".r

}
