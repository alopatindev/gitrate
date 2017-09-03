package hiregooddevs.analysis

import github._
import hiregooddevs.utils.HttpClient

import org.apache.log4j.{Level, Logger}

import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}

import org.apache.spark.streaming._
import org.apache.spark.streaming.StreamingContext._

object Main {

  def main(args: Array[String]): Unit = {
    import sys.props

    val properties = Seq(
      "stream.batchDurationSeconds",
      "github.apiToken",
      "github.maxResults",
      "github.maxRepositories",
      "github.maxPinnedRepositories",
      "github.maxLanguages"
    ).flatMap(props.get)

    properties match {
      case Seq(batchDuration, apiToken, maxResults, maxRepositories, maxPinnedRepositories, maxLanguages) =>
        run(Seconds(batchDuration.toLong),
            GithubConf(apiToken, maxResults, maxRepositories, maxPinnedRepositories, maxLanguages))
      case _ =>
        Logger.getRootLogger().error(s"Invalid configuration $properties")
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
    val queries =
      sc.cassandraTable[GithubSearchQuery]("hiregooddevs", "github_search_queries")
        .select("language",
                "filename",
                "min_repo_size_kib" as "minRepoSizeKiB",
                "max_repo_size_kib" as "maxRepoSizeKiB",
                "pattern")
        .where("enabled = true")
        .collect()
    val receiver = new GithubReceiver(githubConf, queries) with HttpClient
    val stream = ssc.receiverStream(receiver)
    // TODO: checkpoint

    stream.print()
    ssc.start()
    ssc.awaitTermination()

    sc.stop()
  }

}
