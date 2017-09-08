package gitrate.analysis.github

case class GithubReposParser() {

  import java.util.Date

  import org.apache.commons.codec.binary.Base64.decodeBase64
  import org.apache.spark.sql.catalyst.util.DateTimeUtils.stringToTime

  import play.api.libs.json._

  def parseWithOwner(r: JsValue): Option[(JsValue, GithubRepo)] =
    (r \ "id",
     r \ "name",
     r \ "createdAt",
     r \ "pushedAt",
     r \ "primaryLanguage" \ "name",
     r \ "languages" \ "nodes",
     r \ "owner") match {
      case (JsDefined(JsString(idBase64)),
            JsDefined(JsString(name)),
            JsDefined(JsString(created)),
            JsDefined(JsString(updated)),
            JsDefined(JsString(primaryLanguage)),
            JsDefined(JsArray(languages)),
            JsDefined(owner)) =>
        val repo =
          GithubRepo(idBase64,
                     name,
                     stringToTime(created),
                     stringToTime(updated),
                     primaryLanguage,
                     languages.toStringSeq,
                     isFork = false,
                     isMirror = false)
        Some(owner -> repo)
      case _ => None
    }

  def parse(r: JsValue, expectedOwnerId: String): Option[GithubRepo] = {
    (r \ "id",
     r \ "name",
     r \ "owner" \ "id",
     r \ "isFork",
     r \ "isMirror",
     r \ "createdAt",
     r \ "pushedAt",
     r \ "primaryLanguage" \ "name",
     r \ "languages" \ "nodes") match {
      case (JsDefined(JsString(idBase64)),
            JsDefined(JsString(name)),
            JsDefined(JsString(ownerId)),
            JsDefined(JsBoolean(isFork)),
            JsDefined(JsBoolean(isMirror)),
            JsDefined(JsString(created)),
            JsDefined(JsString(updated)),
            JsDefined(JsString(primaryLanguage)),
            JsDefined(JsArray(languages))) =>
        val repo =
          GithubRepo(idBase64,
                     name,
                     stringToTime(created),
                     stringToTime(updated),
                     primaryLanguage,
                     languages.toStringSeq,
                     isFork,
                     isMirror)
        Some(ownerId -> repo)
      case _ => None
    }
  }.filter { case (ownerId, _) => ownerId == expectedOwnerId }
    .map { case (_, repo) => repo }

  implicit class JsArrayConverter(array: Seq[JsValue]) {

    def toStringSeq: Seq[String] =
      array
        .map(_.asOpt[String])
        .flatten

  }

}
