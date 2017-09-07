package gitrate.analysis

package object github {

  import java.util.Date

  import org.apache.commons.codec.binary.Base64.decodeBase64
  import org.apache.spark.sql.catalyst.util.DateTimeUtils.stringToTime

  import play.api.libs.json._

  case class GithubUsersParser(val result: JsValue,
                               val minRepoAgeDays: Int,
                               val minOwnerToAllCommitsRatio: Float,
                               val supportedLanguages: Seq[String],
                               val minTargetRepos: Int) {

    def user(user: String): GithubUser = ???

    def users: Seq[GithubUser] = {
      val nodes: Seq[JsValue] = (result \ "nodes") match {
        case JsDefined(JsArray(nodes)) => nodes
        case _                         => Seq()
      }
      val repos: Seq[_] = nodes // TODO: set type
        .flatMap(parseRepoWithOwner)
        .map {
          case (o: JsValue, repo: GithubRepo) =>
            (o \ "id", o \ "login", o \ "pinnedRepositories" \ "nodes", o \ "repositories" \ "nodes") -> repo
        }
        .flatMap {
          case ((JsDefined(JsString(idBase64)),
                 JsDefined(JsString(login)),
                 JsDefined(JsArray(pinnedRepos)),
                 JsDefined(JsArray(repos))),
                repo: GithubRepo) =>
            val userId: Option[String] = parseUserId(idBase64)
            userId.map(id => (id, login, pinnedRepos, repos) -> repo)
        }
        .flatMap {
          case ((userId: String, login: String, pinnedReposRaw: Seq[JsValue], reposRaw: Seq[JsValue]),
                repo: GithubRepo) =>
            val repos = reposRaw.map(r => parseRepo(r, userId))
            val pinnedRepos = pinnedReposRaw.map(r => parseRepo(r, userId))
            ???
        }
      ???
    }

    def targetUsers: Seq[GithubUser] = ???

    private def parseUserId(idBase64: String): Option[String] = ???

    private def parseRepoWithOwner(r: JsValue): Option[(JsValue, GithubRepo)] =
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
          val isFork = false // part of a search query
          val isMirror = false // part of a search query
          val repo =
            GithubRepo(idBase64,
                       name,
                       stringToTime(created),
                       stringToTime(updated),
                       primaryLanguage,
                       languages.toStringSeq,
                       isFork,
                       isMirror)
          Some(owner -> repo)
        case _ => None
      }

    // FIXME: move to GithubRepo?
    private def parseRepo(r: JsValue, expectedOwnerId: String): Option[GithubRepo] = {
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

  }

  // FIXME: split into modules

  case class GithubUser(val login: String) {

    val email: Option[String] = ???
    def repo(name: String): GithubRepo = ???
    def repos: Seq[GithubRepo] = ???
    def targetRepos: Seq[GithubRepo] = ???
    val isValid: Boolean = ???
    val isTarget: Boolean = ???

  }

  case class GithubRepo(val idBase64: String,
                        val name: String,
                        val created: Date,
                        val updated: Date,
                        val primaryLanguage: String,
                        val languages: Seq[String],
                        val isFork: Boolean,
                        val isMirror: Boolean) {

    //case class GithubRepo(val name: String = ???) {
    val isOld: Boolean = ???
    val hasMostlyCommitsByOwner: Boolean = ???
    val userIsOwner: Boolean = ???
    val isLanguageSupported: Boolean = ???
    val isTarget: Boolean = ???

  }

  private def base64ToString(data: String): String =
    new String(decodeBase64(data))

  implicit class JsArrayConverter(array: Seq[JsValue]) {

    def toStringSeq: Seq[String] =
      array
        .map(_.asOpt[String])
        .flatten

  }

}
