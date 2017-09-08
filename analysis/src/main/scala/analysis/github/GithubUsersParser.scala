package gitrate.analysis.github

import java.util.Date

import org.apache.commons.codec.binary.Base64.decodeBase64
import org.apache.spark.sql.catalyst.util.DateTimeUtils.stringToTime

import play.api.libs.json._

case class GithubUsersParser(val result: JsValue,
                             val minRepoAgeDays: Int,
                             val minOwnerToAllCommitsRatio: Float,
                             val supportedLanguages: Seq[String],
                             val minTargetRepos: Int) {

  val reposParser = GithubReposParser()

  def user(user: String): GithubUser = ???

  def users: Seq[GithubUser] = {
    val nodes: Seq[JsValue] = (result \ "nodes") match {
      case JsDefined(JsArray(nodes)) => nodes
      case _                         => Seq()
    }
    val repos: Seq[_] = nodes // TODO: set type
      .flatMap(reposParser.parseWithOwner)
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
          val repos = reposRaw.map(r => reposParser.parse(r, userId))
          val pinnedRepos = pinnedReposRaw.map(r => reposParser.parse(r, userId))
          ???
      }
    ???
  }

  def targetUsers: Seq[GithubUser] = ???

  // FIXME: split into modules

  private def base64ToString(data: String): String =
    new String(decodeBase64(data))

  private def parseUserId(idBase64: String): Option[String] = ???

}
