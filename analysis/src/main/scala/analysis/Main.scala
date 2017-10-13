package analysis

import github.{GithubConf, GithubExtractor, GithubReceiver, GithubSearchInputDStream, GithubSearchQuery, GithubUser}
import utils.{HttpClientFactory, LogUtils, ResourceUtils, SparkUtils}
import utils.HttpClientFactory.{HttpGetFunction, HttpPostFunction}
import utils.SparkUtils.RDDUtils

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext
import org.apache.spark.sql.{Dataset, Row, SparkSession}
import play.api.libs.json.{JsValue, Json}

object Main extends LogUtils with SparkUtils {

  def appConfig: Config = ConfigFactory.load("app.conf")

  def main(args: Array[String]): Unit = {
    val httpGetBlocking: HttpGetFunction[JsValue] = HttpClientFactory.getFunction(Json.parse)
    val httpPostBlocking: HttpPostFunction[JsValue, JsValue] = HttpClientFactory.postFunction(Json.parse)

    val githubConf = GithubConf(appConfig, httpGetBlocking, httpPostBlocking)
    run(githubConf)
  }

  private def run(githubConf: GithubConf): Unit = {
    val _ = getOrCreateSparkContext()

    val sparkSession = getOrCreateSparkSession()
    import sparkSession.implicits._

    val ssc = createStreamingContext()

    val warningsToGradeCategory: Dataset[WarningToGradeCategory] =
      Postgres
        .executeSQL(DatabaseQueries.loadWarningsToGradeCategory)
        .as[WarningToGradeCategory]
        .cache()

    val weightedTechnologies: Seq[String] =
      Postgres
        .executeSQL(DatabaseQueries.loadWeightedTechnologies)
        .as[String]
        .collect()

    // TODO: checkpoint
    val stream = new GithubSearchInputDStream(ssc, githubConf, loadQueries, storeReceiverResult)

    stream
      .foreachRDD { rawGithubResult: RDD[String] =>
        val currentRepositories: Dataset[Row] = Postgres.getTable("repositories")
        val githubExtractor = new GithubExtractor(githubConf, currentRepositories)
        val users: Iterable[GithubUser] = githubExtractor.parseAndFilterUsers(rawGithubResult)
        implicit val sparkContext: SparkContext = rawGithubResult.sparkContext
        implicit val sparkSession: SparkSession = rawGithubResult.toSparkSession
        val grader = new Grader(appConfig, warningsToGradeCategory, weightedTechnologies)
        val gradedRepositories: Iterable[GradedRepository] = grader.gradeUsers(users)
        logInfo(s"gradedRepositories=${gradedRepositories.toList}")
      // saveAnalysisResult(users, gradedRepositories)
      }

    ssc.start()
    ssc.awaitTermination()

    ssc.stop(stopSparkContext = true, stopGracefully = true)
  }

  // runs on executor
  private def loadQueries(): Seq[GithubSearchQuery] = {
    logInfo()

    val sparkSession = getOrCreateSparkSession()
    import sparkSession.implicits._

    Postgres
      .executeSQL(DatabaseQueries.loadQueries)
      .as[GithubSearchQuery]
      .collect()
      .toSeq
  }

  // runs on executor
  private def storeReceiverResult(receiver: GithubReceiver, result: String): Unit = receiver.store(result)

  private def saveAnalysisResult(users: Iterable[GithubUser], gradedRepositories: Iterable[GradedRepository]): Unit = {
    val query = DatabaseQueries.buildAnalysisResultQuery(users, gradedRepositories)

    val _ = Postgres
      .executeSQL(query)
      .collect()
  }

}
