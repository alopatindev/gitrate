package controllers

import com.taykey.twitterlocationparser.dto.LocationType
import com.taykey.twitterlocationparser.{DefaultLocationParser, dto}

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

import QueryParser._

import scala.annotation.tailrec

class QueryParser(predicates: Map[String, Predicate]) {

  import Query._

  case class Location(country: Option[String], city: Option[String])

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
    val location = parseLocation(items(keys.unknown).mkString(" "))
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

  def parseLocation(location: String): Location =
    Option(locationParser.parseText(location)).map(rawLocation => (rawLocation.getType, rawLocation)) match {
      case Some((LocationType.City, rawLocation)) =>
        val country = getCountry(rawLocation)
        val city = Option(rawLocation.getName)
        Location(country = country, city = city)
      case Some((LocationType.Country, rawLocation)) => Location(country = Option(rawLocation.getName), city = None)
      case Some((LocationType.State, rawLocation)) =>
        val country = getCountry(rawLocation)
        Location(country = country, city = None)
      case _ => Location(country = None, city = None)
    }

  private def getCountry(rawLocation: dto.Location): Option[String] =
    Option(
      locationParser.getLocationDao
        .getCountryByCode(rawLocation.getCountryCode)
        .getName)

  private[this] val locationParser: DefaultLocationParser = new DefaultLocationParser

  private[this] val tokenRegex = """^([.,;'"]*)(.+?)([.,;'"]*)$""".r

}
