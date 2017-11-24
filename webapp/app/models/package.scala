package object models {

  type Lexemes = Seq[String]
  type TokenToLexemes = Map[String, Lexemes]

  object TokenToLexemes {

    def empty: TokenToLexemes =
      Seq(TokenTypes.language,
          TokenTypes.technology,
          TokenTypes.city,
          TokenTypes.country,
          TokenTypes.stopWord,
          TokenTypes.githubLogin)
        .map(_ -> Seq())
        .toMap

  }

  object TokenTypes {
    val language = "language"
    val technology = "technology"
    val city = "city"
    val country = "country"
    val stopWord = "stopWord"
    val githubLogin = "githubLogin"
  }

}
