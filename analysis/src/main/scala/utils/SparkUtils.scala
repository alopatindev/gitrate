package gitrate.utils

import com.typesafe.config.Config

import org.apache.spark.sql.Row
import org.apache.spark.sql.{Dataset, SparkSession}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}

trait SparkUtils {

  def appConfig: Config

  private val sparkConf = new SparkConf()
    .setAppName("AnalyzeGithubUsers")
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
          Map("url" -> s"jdbc:postgresql:${Database}",
              "user" -> User,
              "dbtable" -> table,
              "driver" -> "org.postgresql.Driver"))
        .load

    def executeSQL(query: String): Dataset[Row] = getTable(s"""(${query}) as tmp""")

    private val Config = appConfig.getConfig("db.postgresql")
    private val Database = Config.getString("database")
    private val User = Config.getString("user")

  }

  private val batchDuration = Seconds(appConfig.getDuration("stream.batchDuration").getSeconds)

}
