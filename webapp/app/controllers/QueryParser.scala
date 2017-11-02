package controllers

import common.LocationParser

import scala.annotation.tailrec

object Query {

  type Items = Map[String, List[String]]

  object keys {
    val languages = "languages"
    val technologies = "technologies"
    val cities = "cities"
    val countries = "countries"
    val stopWords = "stopWords"
    val unknown = "unknown"
    val byPriority = Seq(stopWords, languages, technologies, cities, countries, unknown)
  }

  def emptyItems: Items = keys.byPriority.map(_ -> List()).toMap

}

case class Query(rawQuery: String, items: Query.Items)

object QueryParser {

  type Predicate = String => Boolean

}

class QueryParser(predicates: Map[String, QueryParser.Predicate]) {

  import Query._

  def parseQuery(rawQuery: String): Query = {
    @tailrec
    def helper(tokens: List[String], items: Items): Items = tokens match {
      case token :: tail =>
        val key = keys.byPriority
          .filter(predicates(_)(token))
          .head
        val value: List[String] = items(key) :+ token
        val newItems = items + (key -> value)
        helper(tail, newItems)
      case Nil => items
    }

    val items = helper(tokenize(rawQuery), emptyItems)

    val location = LocationParser.parse(items(keys.unknown).mkString(" "))
    val cities = keys.cities -> location.city.map(List(_)).getOrElse(List())
    val countries = keys.countries -> location.country.map(List(_)).getOrElse(List())

    Query(rawQuery, items + cities + countries + (keys.unknown -> List()))
  }

  def tokenize(rawQuery: String): List[String] =
    rawQuery
      .split(' ')
      .flatMap(_.split("""[/\\]"""))
      .filter(_.nonEmpty)
      .toList
      .flatMap {
        case tokenRegex(_, token, _) => Some(token)
        case _                       => None
      }

  private[this] val tokenRegex = """^([.,;'"]*)(.+?)([.,;'"]*)$""".r

}
