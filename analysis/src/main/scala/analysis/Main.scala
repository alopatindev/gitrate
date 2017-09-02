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
    sys.props.get("app.githubToken") match {
      case Some(githubToken) => run(githubToken)
      case None              => Logger.getRootLogger().error("System property app.githubToken is not found")
    }
  }

  private def run(githubToken: String): Unit = {
    // TODO: load from database
    val queries = Seq(
      GithubSearchQuery(language = "JavaScript", filename = ".eslintrc.*", minRepoSizeKiB = 10, maxRepoSizeKiB = 2048),
      GithubSearchQuery(language = "JavaScript", filename = ".travis.yml", minRepoSizeKiB = 10, maxRepoSizeKiB = 2048)
    )

    val sc = new SparkContext(conf)
    val ssc = new StreamingContext(sc, Seconds(5))

    val receiver = new GithubReceiver(githubToken, queries) with HttpClient
    val stream = ssc.receiverStream(receiver)
    // TODO: checkpoint

    stream.print()
    ssc.start()
    ssc.awaitTermination()

    sc.stop()
  }

}
