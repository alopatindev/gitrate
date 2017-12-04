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

package analysis

import common.LocationParser
import controllers.UserController.AnalysisResult
import controllers.{GithubController, GraderController, UserController}
import github.{GithubConf, GithubExtractor, GithubReceiver, GithubSearchInputDStream, GithubUser}
import utils.HttpClientFactory.{HttpGetFunction, HttpPostFunction}
import utils.SparkUtils.DurationUtils
import utils.SparkUtils.RDDUtils
import utils.{AppConfig, HttpClientFactory, LogUtils, ResourceUtils, SparkUtils}

import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext
import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.Duration
import play.api.libs.json.{JsValue, Json}

object Main extends AppConfig with LogUtils with ResourceUtils with SparkUtils {

  def main(args: Array[String]): Unit = {
    val httpGetBlocking: HttpGetFunction[JsValue] = HttpClientFactory.getFunction(Json.parse)
    val httpPostBlocking: HttpPostFunction[JsValue, JsValue] = HttpClientFactory.postFunction(Json.parse)

    val githubConf = GithubConf(appConfig, httpGetBlocking, httpPostBlocking)
    run(githubConf)
  }

  private def run(githubConf: GithubConf): Unit = {
    initializeSpark()
    val ssc = createStreamingContext()

    ssc.checkpoint(appConfig.getString("stream.checkpointPath"))
    val checkpointInterval: Duration = appConfig
      .getDuration("stream.checkpointInterval")
      .toSparkDuration

    val stream = new GithubSearchInputDStream(ssc, githubConf, GithubController.loadQueries, (queryIndex) => {
      val _ = GithubController.saveReceiverState(queryIndex)
    }, storeReceiverResult)
    stream
      .checkpoint(checkpointInterval)
      .foreachRDD { rawGithubResult: RDD[String] =>
        val githubExtractor = new GithubExtractor(githubConf, GithubController.loadAnalyzedRepositories)
        val users: Iterable[GithubUser] = githubExtractor.parseAndFilterUsers(rawGithubResult)

        implicit val sparkContext: SparkContext = rawGithubResult.sparkContext
        implicit val sparkSession: SparkSession = rawGithubResult.toSparkSession
        val grader = new Grader(appConfig, GraderController.warningsToGradeCategory, GraderController.gradeCategories)

        val gradedRepositories: Iterable[GradedRepository] = grader.processUsers(users.toSeq)

        if (gradedRepositories.nonEmpty) {
          val userToLocation: Map[Int, LocationParser.Location] = users
            .flatMap(user => user.location.map(user.id -> _))
            .toMap
            .mapValues(LocationParser.parse)

          val analysisResult = AnalysisResult(users, gradedRepositories, userToLocation)
          val _ = UserController.saveAnalysisResult(analysisResult)
        }
      }

    ssc.start()
    ssc.awaitTermination()

    ssc.stop(stopSparkContext = true, stopGracefully = true)
  }

  // runs on executor
  private def storeReceiverResult(receiver: GithubReceiver, result: String): Unit = receiver.store(result)

}
