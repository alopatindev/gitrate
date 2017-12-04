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

import play.api.libs.json.{JsPath, Json, Writes}
import play.api.libs.functional.syntax._

object SearcherModel {

  type RawUser = (Int, String, String, Seq[String], Seq[String], Seq[String])

  case class Grade(category: String, value: Double)
  case class Language(language: String, verified: Boolean)
  case class Technology(technology: String, verified: Boolean)
  case class User(id: Int,
                  githubLogin: String,
                  fullName: String,
                  avgGrade: Double,
                  grades: Seq[Grade],
                  languages: Seq[Language],
                  technologies: Seq[Technology])
  case class SearchResults(users: Seq[User])

  implicit val languageWrites: Writes[Language] = (
    (JsPath \ "language").write[String] and
      (JsPath \ "verified").write[Boolean]
  )(unlift(Language.unapply))

  implicit val technologyWrites: Writes[Technology] = (
    (JsPath \ "technology").write[String] and
      (JsPath \ "verified").write[Boolean]
  )(unlift(Technology.unapply))

  implicit val gradeWrites: Writes[Grade] = (
    (JsPath \ "category").write[String] and
      (JsPath \ "value").write[Double]
  )(unlift(Grade.unapply))

  implicit val userWrites: Writes[User] = (
    (JsPath \ "id").write[Int] and
      (JsPath \ "githubLogin").write[String] and
      (JsPath \ "fullName").write[String] and
      (JsPath \ "avgGrade").write[Double] and
      (JsPath \ "grades").write[Seq[Grade]] and
      (JsPath \ "languages").write[Seq[Language]] and
      (JsPath \ "technologies").write[Seq[Technology]]
  )(unlift(User.unapply))

  implicit val searchResultsWrites = new Writes[SearchResults] {
    def writes(searchResults: SearchResults) = Json.obj(
      "users" -> searchResults.users
    )
  }

}
