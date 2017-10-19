package analysis

import controllers.UserController
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

  def appConfig: Config = ConfigFactory.load("application.conf")

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
        .executeSQL(resourceToString("/db/loadWarningsToGradeCategory.sql")) // TODO: test
        .as[WarningToGradeCategory]
        .cache()

    val gradeCategories: Dataset[GradeCategory] =
      Postgres
        .executeSQL("SELECT category AS gradeCategory FROM grade_categories") // TODO: test
        .as[GradeCategory]
        .cache()

    // TODO: checkpoint
    val stream = new GithubSearchInputDStream(ssc, githubConf, loadQueries, storeReceiverResult)

    stream
      .foreachRDD { rawGithubResult: RDD[String] =>
        val currentRepositories: Dataset[Row] = Postgres.getTable("repositories") // FIXME
        val githubExtractor = new GithubExtractor(githubConf, currentRepositories)

        val users: Iterable[GithubUser] = githubExtractor.parseAndFilterUsers(rawGithubResult)

        implicit val sparkContext: SparkContext = rawGithubResult.sparkContext
        implicit val sparkSession: SparkSession = rawGithubResult.toSparkSession
        val grader = new Grader(appConfig, warningsToGradeCategory, gradeCategories)

        val gradedRepositories: Iterable[GradedRepository] = grader.processUsers(users)
        logInfo(s"graded ${gradedRepositories.size} repositories!")

        if (gradedRepositories.nonEmpty) {
          val _ = UserController.saveAnalysisResult(users, gradedRepositories)
        }
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

    val query: String = resourceToString("/db/loadQueries.sql") // TODO: test
    Postgres
      .executeSQL(query)
      .as[GithubSearchQuery]
      .collect()
      .toSeq
  }

  // runs on executor
  private def storeReceiverResult(receiver: GithubReceiver, result: String): Unit = receiver.store(result)

}
