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

package analysis.github

import utils.HttpClientFactory.{HttpGetFunction, HttpPostFunction}

import com.typesafe.config.Config
import java.time.Duration
import play.api.libs.json.JsValue
import scala.collection.JavaConverters._

case class GithubConf(appConfig: Config,
                      httpGetBlocking: HttpGetFunction[JsValue],
                      httpPostBlocking: HttpPostFunction[JsValue, JsValue]) {

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
