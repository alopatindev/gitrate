package gitrate.analysis

import github.{GithubConf, GithubExtractor, GithubReceiver, GithubSearchQuery, GithubSearchInputDStream, GithubUser}
import gitrate.utils.HttpClientFactory
import gitrate.utils.HttpClientFactory.{HttpGetFunction, HttpPostFunction}
import gitrate.utils.SparkUtils.RDDUtils
import gitrate.utils.{LogUtils, SparkUtils}

import com.typesafe.config.{Config, ConfigFactory}

import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext
import org.apache.spark.sql.{Dataset, Row, SparkSession}

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

    val sparkSession = getOrCreateSparkSession()
    import sparkSession.implicits._

    val ssc = createStreamingContext()

    val warningsToGradeCategory: Dataset[WarningToGradeCategory] =
      Postgres
        .executeSQL("""
SELECT
  warnings.warning,
  tags.tag,
  grade_categories.category AS gradeCategory
FROM warnings
INNER JOIN grade_categories ON grade_categories.id = warnings.grade_category_id
INNER JOIN tags ON tags.id = warnings.tag_id
""")
        .as[WarningToGradeCategory]
        .cache()

    val weightedTechnologies: Seq[String] =
      Postgres
        .executeSQL(s"""
SELECT tag
FROM tags
INNER JOIN tag_categories ON tag_categories.id = tags.category_id
WHERE
  tag_categories.category_rest_id = 'technologies'
  AND tags.weight > 0
LIMIT 1
""")
        .as[String]
        .collect()

    // TODO: checkpoint
    val stream = new GithubSearchInputDStream(ssc, githubConf, loadQueries, storeResult)

    stream
      .foreachRDD { rawGithubResult: RDD[String] =>
        val currentRepositories: Dataset[Row] = Postgres.getTable("repositories")
        val githubExtractor = new GithubExtractor(githubConf, currentRepositories)
        val users: Iterable[GithubUser] = githubExtractor.parseAndFilterUsers(rawGithubResult)
        implicit val sparkContext: SparkContext = rawGithubResult.sparkContext
        implicit val sparkSession: SparkSession = rawGithubResult.toSparkSession
        val grader = new Grader(appConfig, warningsToGradeCategory, weightedTechnologies)
        val gradedRepositories: Iterable[GradedRepository] = grader.gradeUsers(users)
      // TODO: save(users, gradedRepositories)
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
