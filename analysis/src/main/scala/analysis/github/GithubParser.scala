package gitrate.analysis.github

import java.util.Date

import org.apache.spark.sql.catalyst.util.DateTimeUtils.stringToTime

import play.api.libs.json._

class GithubParser(val reposParser: GithubReposParser) {

  def parseUsersAndRepos(input: JsValue): Seq[GithubUser] = {
    val nodes: Seq[JsValue] = (input \ "nodes") match {
      case JsDefined(JsArray(nodes)) => nodes
      case _                         => Seq()
    }
    nodes
      .flatMap(reposParser.parseAndFilterWithOwner)
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
          val userId: Option[String] = parseAndFilterUserId(idBase64)
          userId.map(id => (id, login, pinnedRepos, repos) -> repo)
      }
      .map {
        case ((userId: String, login: String, pinnedReposRaw: Seq[JsValue], reposRaw: Seq[JsValue]),
              repo: GithubRepo) =>
          val pinnedRepos = pinnedReposRaw.flatMap(r => reposParser.parseAndFilter(r))
          val repos = reposRaw.flatMap(r => reposParser.parseAndFilter(r))
          // TODO: remove duplicates
          val allRepos = repo :: (pinnedRepos ++ repos).toList
          GithubUser(userId, login, allRepos)
      }
  }

}
