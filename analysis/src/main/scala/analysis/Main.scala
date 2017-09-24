package gitrate.analysis

import github.{GithubConf, GithubReceiver, GithubSearchQuery, GithubSearchInputDStream}
import github.parser.{GithubParser, GithubUser}
import gitrate.utils.HttpClientFactory
import gitrate.utils.HttpClientFactory.{HttpGetFunction, HttpPostFunction}
import gitrate.utils.{LogUtils, SparkUtils}

import com.typesafe.config.{Config, ConfigFactory}
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

    stream.foreachRDD { rdd =>
      val githubParser = new GithubParser(githubConf)
      githubParser.parseAndFilterUsers(rdd).foreach { (user: GithubUser) =>
        logInfo(s"repo id=${user.id} login=${user.login} repos=${user.repositories}")
      }
    }

    // val receiver = new GithubReceiver(githubConf, onLoadQueries, onStoreResult)
    // val stream = ssc.receiverStream(receiver)
    // stream.print()

    ssc.start()
    ssc.awaitTermination()

    ssc.stop(stopSparkContext = true, stopGracefully = true)
  }

  // runs on executor
  def loadQueries(): Seq[GithubSearchQuery] = {
    logInfo()
    val sparkSession = getOrCreateSparkSession()
    import sparkSession.implicits._
    executeSQL("""
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
""").as[GithubSearchQuery]
      .collect()
      .toSeq
  }

  // runs on executor
  def storeResult(receiver: GithubReceiver, result: String): Unit = receiver.store(result)

}
