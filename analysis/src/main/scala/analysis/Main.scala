package hiregooddevs.analysis

import github._
import hiregooddevs.utils.{HttpClient, LogUtils}

import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}

import org.apache.spark.streaming._
import org.apache.spark.streaming.StreamingContext._

object Main extends LogUtils {

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
        val batchDuration = Seconds(batchDurationSeconds.toLong)
        val githubConf = GithubConf(apiToken,
                                    maxResults.toInt,
                                    maxRepositories.toInt,
                                    maxPinnedRepositories.toInt,
                                    maxLanguages.toInt)
        run(batchDuration, githubConf)
      case _ => logError(s"Invalid configuration $properties")
    }
  }

  private def run(batchDuration: Duration, githubConf: GithubConf): Unit = {
    import com.datastax.spark.connector._

    val sparkConf = new SparkConf() // scalastyle:ignore
      .setAppName("FindGithubUsers")
      .setMaster("local[*]")
    val sc = new SparkContext(sparkConf)
    val ssc = new StreamingContext(sc, batchDuration)

    // FIXME: ALLOW FILTERING?
    def loadQueriesOnExecutor =
      SparkContext
        .getOrCreate()
        .cassandraTable[GithubSearchQuery]("hiregooddevs", "github_search_queries")
        .select("language",
                "filename",
                "min_repo_size_kib" as "minRepoSizeKiB",
                "max_repo_size_kib" as "maxRepoSizeKiB",
                "pattern")
        .where("enabled = true")
        .collect()
    val receiver = new GithubReceiver(githubConf, loadQueriesOnExecutor) with HttpClient
    val stream = ssc.receiverStream(receiver)
    // TODO: checkpoint

    stream.print()
    ssc.start()
    ssc.awaitTermination()

    sc.stop()
  }

}
