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
