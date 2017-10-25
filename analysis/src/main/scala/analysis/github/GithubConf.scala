package analysis.github

import utils.HttpClientFactory.{HttpGetFunction, HttpPostFunction}

import com.typesafe.config.Config
import java.time.Duration
import play.api.libs.json.JsValue
import scala.collection.JavaConverters._

case class GithubConf(val appConfig: Config,
                      val httpGetBlocking: HttpGetFunction[JsValue],
                      val httpPostBlocking: HttpPostFunction[JsValue, JsValue]) {

  private val githubConfig = appConfig.getConfig("github")

  val apiToken: String = githubConfig.getString("apiToken")
  val maxResults: Int = githubConfig.getInt("maxResults")
  val maxRepositories: Int = githubConfig.getInt("maxRepositories")
  val maxPinnedRepositories: Int = githubConfig.getInt("maxPinnedRepositories")
  val maxLanguages: Int = githubConfig.getInt("maxLanguages")
  val minRepoAge: Duration = githubConfig.getDuration("minRepoAge")
  val minTargetRepositories: Int = githubConfig.getInt("minTargetRepositories")
  val minOwnerToAllCommitsRatio: Double = githubConfig.getDouble("minOwnerToAllCommitsRatio")
  val minRepositoryUpdateInterval: Duration = githubConfig.getDuration("minRepositoryUpdateInterval")
  val supportedLanguages: Set[String] = githubConfig.getStringList("supportedLanguages").asScala.toSet

}
