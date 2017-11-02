package common

import com.taykey.twitterlocationparser.dto.LocationType
import com.taykey.twitterlocationparser.{DefaultLocationParser, dto}

object LocationParser {

  case class Location(country: Option[String], city: Option[String])

  def parse(text: String): Location =
    Option(parser.parseText(text)).map(rawLocation => (rawLocation.getType, rawLocation)) match {
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
      parser.getLocationDao
        .getCountryByCode(rawLocation.getCountryCode)
        .getName)

  private[this] val parser: DefaultLocationParser = new DefaultLocationParser

}
