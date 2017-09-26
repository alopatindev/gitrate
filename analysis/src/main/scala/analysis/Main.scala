package gitrate.analysis

import github.{GithubConf, GithubExtractor, GithubReceiver, GithubSearchQuery, GithubSearchInputDStream, GithubUser}
import gitrate.utils.HttpClientFactory
import gitrate.utils.HttpClientFactory.{HttpGetFunction, HttpPostFunction}
import gitrate.utils.{LogUtils, SparkUtils}
import gitrate.utils.SparkUtils.RDDUtils

import com.typesafe.config.{Config, ConfigFactory}

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Dataset, Row}

import play.api.libs.json.{Json, JsValue}

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
    val ssc = createStreamingContext()

    // TODO: checkpoint
    val stream = new GithubSearchInputDStream(ssc, githubConf, loadQueries, storeResult)

    // TODO: inject and broadcast table
    val warningsToGradeCategory: Dataset[Row] =
      Postgres.executeSQL("""
SELECT
  warnings.warning,
  tags.tag,
  grade_categories.category AS grade_category
FROM warnings
JOIN grade_categories ON grade_categories.id = warnings.grade_category_id
JOIN tags ON tags.id = warnings.tag_id
""")

    stream
      .foreachRDD { rawGithubResult: RDD[String] =>
        val currentRepositories: Dataset[Row] = Postgres.getTable("repositories") // TODO: inject table
        val githubExtractor = new GithubExtractor(githubConf, currentRepositories)
        val users: Iterable[GithubUser] = githubExtractor.parseAndFilterUsers(rawGithubResult)
        implicit val sparkContext = rawGithubResult.sparkContext
        implicit val sparkSession = rawGithubResult.toSparkSession
        new Grader(appConfig, warningsToGradeCategory).grade(users)
      }

    ssc.start()
    ssc.awaitTermination()

    ssc.stop(stopSparkContext = true, stopGracefully = true)
  }

  // runs on executor
  def loadQueries(): Seq[GithubSearchQuery] = {
    logInfo()

    val sparkSession = getOrCreateSparkSession()
    import sparkSession.implicits._

    Postgres
      .executeSQL("""
SELECT
  language,
  filename,
  min_repo_size_kib AS minRepoSizeKiB,
  max_repo_size_kib AS maxRepoSizeKiB,
  min_stars AS minStars,
  max_stars AS maxStars,
  pattern
FROM github_search_queries
WHERE enabled = true
""")
      .as[GithubSearchQuery]
      .collect()
      .toSeq
  }

  // runs on executor
  def storeResult(receiver: GithubReceiver, result: String): Unit = receiver.store(result)

}
