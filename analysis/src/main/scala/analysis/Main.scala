package analysis

import analysis.TextAnalyzer.{Location, StemToSynonyms}
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

    val stream = new GithubSearchInputDStream(ssc, githubConf, GithubController.loadQueries, storeReceiverResult)
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
          val languageToTechnologyToSynonyms: Iterable[(String, StemToSynonyms)] =
            gradedRepositories.flatMap(repo => TextAnalyzer.technologySynonyms(repo.languageToTechnologies))

          val userToLocation: Map[Int, Location] = users
            .flatMap(user => user.location.map(user.id -> _))
            .toMap
            .mapValues(TextAnalyzer.parseLocation)

          val analysisResult = AnalysisResult(users, gradedRepositories, languageToTechnologyToSynonyms, userToLocation)
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
