package gitrate.analysis

import github.{GithubConf, GithubReceiver, GithubSearchQuery, GithubSearchInputDStream /*, GithubUser*/}
import github.parser._
import gitrate.utils.HttpClientFactory
import gitrate.utils.HttpClientFactory.{HttpGetFunction, HttpPostFunction}
import gitrate.utils.{LogUtils, SparkUtils}

import play.api.libs.json.{Json, JsValue}

object Main extends LogUtils with SparkUtils {

  def main(args: Array[String]): Unit = loadConfig().foreach((run _).tupled)

  private def run(batchDurationSeconds: Int, githubConf: GithubConf): Unit = {
    val _ = createSparkContext()
    val ssc = createStreamingContext(batchDurationSeconds)

    // TODO: checkpoint
    GithubSearchInputDStream
      .createStream(ssc, githubConf, onLoadQueries, onStoreResult)
      .foreachRDD { rdd =>
        val githubParser = new GithubParser(githubConf)
        githubParser.parseAndFilterUsers(rdd).foreach { (user: GithubUser) =>
          println(s"repo id=${user.id} login=${user.login} repos=${user.repos}")
        }
      }

    //val receiver = new GithubReceiver(githubConf, onLoadQueries, onStoreResult)
    //val stream = ssc.receiverStream(receiver)
    //stream.print()

    ssc.start()
    ssc.awaitTermination()

    ssc.stop(stopSparkContext = true, stopGracefully = true)
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

  private def loadConfig(): Option[(Int, GithubConf)] = {
    val httpGetBlocking: HttpGetFunction[JsValue] = HttpClientFactory.getFunction(Json.parse)
    val httpPostBlocking: HttpPostFunction[JsValue, JsValue] = HttpClientFactory.postFunction(Json.parse)

    val properties = Seq(
      "stream.batchDurationSeconds",
      "github.apiToken",
      "github.maxResults",
      "github.maxRepositories",
      "github.maxPinnedRepositories",
      "github.maxLanguages",
      "github.minRepoAgeDays",
      "github.minTargetRepos",
      "github.minOwnerToAllCommitsRatio",
      "github.supportedLanguages"
    ).flatMap(sys.props.get)

    properties match {
      case Seq(batchDurationSeconds,
               apiToken,
               maxResults,
               maxRepositories,
               maxPinnedRepositories,
               maxLanguages,
               minRepoAgeDays,
               minTargetRepos,
               minOwnerToAllCommitsRatio,
               supportedLanguagesRaw) =>
        val githubConf = GithubConf(
          apiToken = apiToken,
          maxResults = maxResults.toInt,
          maxRepositories = maxRepositories.toInt,
          maxPinnedRepositories = maxPinnedRepositories.toInt,
          maxLanguages = maxLanguages.toInt,
          minRepoAgeDays = minRepoAgeDays.toInt,
          minTargetRepos = minTargetRepos.toInt,
          minOwnerToAllCommitsRatio = minOwnerToAllCommitsRatio.toDouble,
          supportedLanguagesRaw = supportedLanguagesRaw,
          httpGetBlocking = httpGetBlocking,
          httpPostBlocking = httpPostBlocking
        )

        Some((batchDurationSeconds.toInt, githubConf))

      case _ =>
        logError(s"Invalid configuration $properties")
        None
    }
  }

}
