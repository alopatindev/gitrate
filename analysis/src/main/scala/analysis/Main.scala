package gitrate.analysis

import gitrate.utils.HttpClientFactory
import gitrate.utils.HttpClientFactory.HttpPostFunction
import gitrate.utils.{LogUtils, SparkUtils}

import github.{GithubConf, GithubReceiver, GithubSearchQuery}
import play.api.libs.json.{Json, JsValue}

object Main extends LogUtils with SparkUtils {

  def main(args: Array[String]): Unit = {
    val properties = Seq(
      "stream.batchDurationSeconds",
      "github.apiToken",
      "github.maxResults",
      "github.maxRepositories",
      "github.maxPinnedRepositories",
      "github.maxLanguages"
    ).flatMap(sys.props.get)

    properties match {
      case Seq(batchDurationSeconds, apiToken, maxResults, maxRepositories, maxPinnedRepositories, maxLanguages) =>
        val githubConf = GithubConf(apiToken,
                                    maxResults.toInt,
                                    maxRepositories.toInt,
                                    maxPinnedRepositories.toInt,
                                    maxLanguages.toInt)
        run(batchDurationSeconds.toInt, githubConf)
      case _ => logError(s"Invalid configuration $properties")
    }
  }

  private def run(batchDurationSeconds: Int, githubConf: GithubConf): Unit = {
    val sc = createSparkContext()
    val ssc = createStreamingContext(batchDurationSeconds)

    val httpPostBlocking: HttpPostFunction[JsValue, JsValue] = HttpClientFactory.postFunction(Json.parse)
    val receiver = new GithubReceiver(githubConf)(httpPostBlocking, onLoadQueries, onStoreResult)
    val stream = ssc.receiverStream(receiver)
    // TODO: checkpoint

    stream.print()
    ssc.start()
    ssc.awaitTermination()

    sc.stop()
  }

  // runs on executor
  def onLoadQueries(): Seq[GithubSearchQuery] = {
    logInfo()
    val query = """
SELECT
  language,
  filename,
  CAST(min_repo_size_kib AS INT) AS minRepoSizeKiB,
  CAST(max_repo_size_kib AS INT) AS maxRepoSizeKiB,
  CAST(min_stars AS INT) AS minStars,
  CAST(max_stars AS INT) AS maxStars,
  pattern
FROM gitrate.github_search_queries
WHERE partition = 0 AND enabled = true;
"""
    executeCQL(query)
      .map(row => GithubSearchQuery(row))
      .toSeq
  }

  // runs on executor
  def onStoreResult(receiver: GithubReceiver, result: String): Unit = receiver.store(result)

}
