package gitrate.analysis.github

import gitrate.utils.HttpClientFactory.HttpGetFunction

import java.net.URL
import play.api.libs.json.{JsValue, JsDefined, JsArray, JsBoolean, JsString}

class GithubReposParser(val minRepoAgeDays: Int,
                        val minOwnerToAllCommitsRatio: Double,
                        val supportedLanguages: Set[String],
                        val minTargetRepos: Int,
                        val httpGetBlocking: HttpGetFunction[JsValue]) {

  def parseRepo(json: JsValue, login: Option[String]): Option[GithubRepo] =
    parseRepoAndOwner(json, login).map { case (repo, _) => repo }

  def parseRepoAndOwner(json: JsValue, login: Option[String] = None): Option[(GithubRepo, Option[GithubRepoOwner])] = {
    val props = (json \ "id",
                 json \ "name",
                 json \ "createdAt",
                 json \ "pushedAt",
                 json \ "primaryLanguage" \ "name",
                 json \ "languages" \ "nodes")

    props match {
      case (JsDefined(JsString(repoIdBase64)),
            JsDefined(JsString(name)),
            JsDefined(JsString(createdRaw)),
            JsDefined(JsString(updatedRaw)),
            JsDefined(JsString(primaryLanguage)),
            JsDefined(JsArray(languages))) =>
        val (isFork, isMirror, owner) = parseOptionalProps(json)

        val ownerLogin = (login, owner) match {
          case (Some(login), _) => Some(login)
          case (_, Some(owner)) => Some(owner.login)
          case _                => None
        }

        val repo = ownerLogin.map(
          login =>
            GithubRepo(repoIdBase64,
                       name,
                       createdRaw,
                       updatedRaw,
                       primaryLanguage,
                       languages.map(_.asOpt[String]).flatten,
                       isFork,
                       isMirror,
                       login,
                       this))

        repo.map(r => (r, owner))
      case _ => None
    }
  }

  private def parseOptionalProps(json: JsValue): (Boolean, Boolean, Option[GithubRepoOwner]) = {
    val (isFork, isMirror) = (json \ "isFork", json \ "isMirror") match {
      case (JsDefined(JsBoolean(isFork)), JsDefined(JsBoolean(isMirror))) =>
        (isFork, isMirror)
      case _ =>
        (false, false) // GraphQL query already has them
    }

    (isFork, isMirror, parseOwner(json))
  }

  private def parseOwner(json: JsValue): Option[GithubRepoOwner] = {
    val owner = (json \ "owner")
    (owner \ "id", owner \ "login", owner \ "pinnedRepositories" \ "nodes", owner \ "repositories" \ "nodes") match {
      case (JsDefined(JsString(userIdBase64)),
            JsDefined(JsString(login)),
            JsDefined(JsArray(pinnedRepos)),
            JsDefined(JsArray(repos))) =>
        parseUserId(userIdBase64).map(userId => GithubRepoOwner(userId, login, pinnedRepos, repos))
      case _ => None
    }
  }

  case class GithubRepoOwner(val userId: Int, val login: String, pinnedReposRaw: Seq[JsValue], reposRaw: Seq[JsValue]) {

    val pinnedRepos = parseRepos(pinnedReposRaw)
    val repos = parseRepos(reposRaw)

    private def parseRepos(repos: Seq[JsValue]): Seq[GithubRepo] =
      repos.flatMap(repo => parseRepo(repo, Some(login)))

  }

  def apiV3Blocking(path: String): JsValue = {
    val url = new URL(s"https://api.github.com/${path}")
    val headers = Map("Accept" -> "application/vnd.github.v3.json")
    httpGetBlocking(url, headers)
  }

}
