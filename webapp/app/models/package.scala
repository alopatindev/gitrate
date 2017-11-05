package object models {

  type Predicate = String => Boolean
  type Lexemes = List[String]

  object TokenTypes {
    val languages = "languages"
    val technologies = "technologies"
    val cities = "cities"
    val countries = "countries"
    val stopWords = "stopWords"
    val unknown = "unknown"
    val parsableWithPredicates = Seq(stopWords, languages, technologies, unknown)
  }

  type TokenToLexemes = Map[String, Lexemes]

  object TokenToLexemes {

    def empty: TokenToLexemes =
      (TokenTypes.parsableWithPredicates ++ Seq(TokenTypes.cities, TokenTypes.countries))
        .map(_ -> List())
        .toMap

  }

}

package models {

  case class Query(rawQuery: String, tokens: TokenToLexemes)

}
