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

object Main extends LogUtils with ResourceUtils with SparkUtils {

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
        .executeSQL(loadWarningsToGradeCategorySQL)
        .as[WarningToGradeCategory]
        .cache()

    val weightedTechnologies: Seq[String] =
      Postgres
        .executeSQL(loadWeightedTechnologiesSQL)
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
      // TODO: saveAnalysisResult(users, gradedRepositories)
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
      .executeSQL(loadQueriesSQL)
      .as[GithubSearchQuery]
      .collect()
      .toSeq
  }

  // runs on executor
  private def storeReceiverResult(receiver: GithubReceiver, result: String): Unit = receiver.store(result)

  private def saveAnalysisResult(users: Iterable[GithubUser], gradedRepositories: Iterable[GradedRepository]): Unit = {
    val query = buildAnalysisResultQuery(users, gradedRepositories)

    val _ = Postgres
      .executeSQL(query)
      .collect()
  }

  private def buildAnalysisResultQuery(users: Iterable[GithubUser],
                                       gradedRepositories: Iterable[GradedRepository]): String = { ??? }

  private val loadQueriesSQL = resourceToString("/db/loadQueries.sql")
  private val loadWarningsToGradeCategorySQL = resourceToString("/db/loadWarningsToGradeCategory.sql")
  private val loadWeightedTechnologiesSQL = resourceToString("/db/loadWeightedTechnologies.sql")

}
