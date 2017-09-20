package gitrate.analysis.github

import gitrate.utils.HttpClientFactory.{HttpGetFunction, HttpPostFunction}

import play.api.libs.json.JsValue

case class GithubConf(val apiToken: String,
                      val maxResults: Int,
                      val maxRepositories: Int,
                      val maxPinnedRepositories: Int,
                      val maxLanguages: Int,
                      val minRepoAgeDays: Int,
                      val minTargetRepos: Int,
                      val minOwnerToAllCommitsRatio: Double,
                      val minRepoUpdateIntervalDays: Int,
                      val minUserUpdateIntervalDays: Int,
                      supportedLanguagesRaw: String,
                      val httpGetBlocking: HttpGetFunction[JsValue],
                      val httpPostBlocking: HttpPostFunction[JsValue, JsValue]) {

  val supportedLanguages: Set[String] = supportedLanguagesRaw.split(",").toSet

}
