package gitrate.utils

import com.datastax.driver.core.Row
import com.datastax.spark.connector.cql.CassandraConnector

import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}

trait SparkUtils {

  private val appName = "gitrate-analysis"

  def createSparkContext(): SparkContext = {
    val sparkConf = new SparkConf()
      .setAppName(appName)
      .setMaster("local[*]")
    new SparkContext(sparkConf)
  }

  def createStreamingContext(batchDurationSeconds: Int): StreamingContext = {
    val sc = SparkContext.getOrCreate()
    val duration = Seconds(batchDurationSeconds.toLong)
    new StreamingContext(sc, duration)
  }

  def executeCQL(query: String): Iterator[Row] = {
    import scala.collection.JavaConverters._

    val conf = SparkContext
      .getOrCreate()
      .getConf

    CassandraConnector(conf).withSessionDo { session =>
      session
        .execute(query)
        .iterator
        .asScala
    }
  }

}
