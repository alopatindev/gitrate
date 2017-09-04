package hiregooddevs.analysis

import github._
import hiregooddevs.utils.{HttpClient, LogUtils}

import org.apache.spark.streaming._
import org.apache.spark.streaming.StreamingContext._
import org.apache.spark.{SparkConf, SparkContext}

import com.datastax.spark.connector._
import com.datastax.spark.connector.cql.CassandraConnector

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
    val sparkConf = new SparkConf() // scalastyle:ignore
      .setAppName("FindGithubUsers")
      .setMaster("local[*]")
    val sc = new SparkContext(sparkConf)
    val ssc = new StreamingContext(sc, batchDuration)

    val receiver = new GithubReceiver(githubConf, loadQueriesOnExecutor) with HttpClient
    val stream = ssc.receiverStream(receiver)
    // TODO: checkpoint

    stream.print()
    ssc.start()
    ssc.awaitTermination()

    sc.stop()
  }

  def loadQueriesOnExecutor: Seq[GithubSearchQuery] = {
    import scala.collection.JavaConverters._

    logInfo()
    val conf = SparkContext
      .getOrCreate()
      .getConf
    CassandraConnector(conf).withSessionDo { session =>
      session
        .execute("""
SELECT
  language,
  filename,
  CAST(min_repo_size_kib AS INT) AS minRepoSizeKiB,
  CAST(max_repo_size_kib AS INT) AS maxRepoSizeKiB,
  CAST(min_stars AS INT) AS minStars,
  CAST(max_stars AS INT) AS maxStars,
  pattern
FROM hiregooddevs.github_search_queries
WHERE partition = 0 AND enabled = true;
""")
        .iterator
        .asScala
        .map(row => GithubSearchQuery(row))
        .toSeq
    }
  }

}
