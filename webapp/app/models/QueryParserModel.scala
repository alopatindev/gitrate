// Copyright 2017 Alexander Lopatin
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package models

object QueryParserModel {

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
