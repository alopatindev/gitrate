package hiregooddevs.analysis

import github._
import hiregooddevs.utils.HttpClient

import org.apache.log4j.{Level, Logger}

import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}

import org.apache.spark.streaming._
import org.apache.spark.streaming.StreamingContext._

object Main {

  val conf = new SparkConf()
    .setAppName("FindGithubUsers")
    .setMaster("local[*]")

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
    // TODO: load from database
    val queries = Seq(
      GithubSearchQuery(language = "JavaScript", filename = ".eslintrc.*", minRepoSizeKiB = 10, maxRepoSizeKiB = 2048),
      GithubSearchQuery(language = "JavaScript", filename = ".travis.yml", minRepoSizeKiB = 10, maxRepoSizeKiB = 2048)
    )

    val sc = new SparkContext(conf)
    val ssc = new StreamingContext(sc, batchDuration)

    val receiver = new GithubReceiver(githubConf, queries) with HttpClient
    val stream = ssc.receiverStream(receiver)
    // TODO: checkpoint

    stream.print()
    ssc.start()
    ssc.awaitTermination()

    sc.stop()
  }

}
