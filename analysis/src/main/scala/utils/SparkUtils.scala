package gitrate.utils

import com.typesafe.config.Config

import org.apache.spark.sql.Row
import org.apache.spark.sql.{Dataset, SparkSession}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}

trait SparkUtils {

  def appConfig: Config

  private val sparkConf = new SparkConf()
    .setAppName("")
    .setMaster("local[*]")

  def getOrCreateSparkContext(): SparkContext =
    SparkContext.getOrCreate(sparkConf)

  def createStreamingContext(): StreamingContext = {
    val sc = SparkContext.getOrCreate()
    new StreamingContext(sc, batchDuration)
  }

  def getOrCreateSparkSession(): SparkSession =
    SparkSession.builder
      .config(sparkConf)
      .getOrCreate()

  def executeSQL(query: String): Dataset[Row] = {
    getOrCreateSparkSession().read
      .format("jdbc")
      .options(
        Map("url" -> s"jdbc:postgresql:${postgresqlDatabase}",
            "user" -> postgresqlUser,
            "dbtable" -> s"""(${query}) as tmp""",
            "driver" -> "org.postgresql.Driver"))
      .load
  }

  private val postgresqlConfig = appConfig.getConfig("db.postgresql")
  private val postgresqlDatabase = postgresqlConfig.getString("database")
  private val postgresqlUser = postgresqlConfig.getString("user")

  private val batchDuration = Seconds(appConfig.getDuration("stream.batchDuration").getSeconds)

}
