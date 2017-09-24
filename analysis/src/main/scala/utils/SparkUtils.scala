package gitrate.utils

import org.apache.spark.sql.Row
import org.apache.spark.sql.{Dataset, SparkSession}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}

trait SparkUtils {

  private val appName = "gitrate-analysis"

  private val sparkConf = new SparkConf()
    .setAppName(appName)
    .setMaster("local[*]")

  def getOrCreateSparkContext(): SparkContext =
    SparkContext.getOrCreate(sparkConf)

  def createStreamingContext(batchDurationSeconds: Int): StreamingContext = {
    val sc = SparkContext.getOrCreate()
    val duration = Seconds(batchDurationSeconds.toLong)
    new StreamingContext(sc, duration)
  }

  def getOrCreateSparkSession(): SparkSession =
    SparkSession.builder
      .config(sparkConf)
      .getOrCreate()

  def executeSQL(query: String): Dataset[Row] = {
    getOrCreateSparkSession().read
      .format("jdbc")
      .options(
        Map("url" -> "jdbc:postgresql:gitrate",
            "user" -> "gitrate", // TODO: move to config
            "dbtable" -> s"""(${query}) as tmp""",
            "driver" -> "org.postgresql.Driver"))
      .load
  }

}
