package utils

import com.typesafe.config.Config

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Dataset, Row, SparkSession}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}

trait SparkUtils {

  def appConfig: Config

  private val sparkConf = new SparkConf()
    .setAppName(appConfig.getString("app.name"))
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

  object Postgres {

    def getTable(table: String): Dataset[Row] =
      getOrCreateSparkSession().read
        .format("jdbc")
        .options(
          Map("url" -> s"jdbc:postgresql:$database",
              "user" -> user,
              "dbtable" -> table,
              "driver" -> "org.postgresql.Driver"))
        .load

    def executeSQL(query: String): Dataset[Row] = getTable(s"""($query) AS tmp""")

    private val config = appConfig.getConfig("db.postgresql")
    private val database = config.getString("database")
    private val user = config.getString("user")

  }

  private val batchDuration = Seconds(appConfig.getDuration("stream.batchDuration").getSeconds)

}

object SparkUtils {

  implicit class RDDUtils(rdd: RDD[_]) {

    def toSparkSession: SparkSession = {
      val conf = rdd.sparkContext.getConf
      SparkSession.builder
        .config(conf)
        .getOrCreate()
    }

  }

}
