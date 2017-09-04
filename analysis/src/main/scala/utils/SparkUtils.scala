package hiregooddevs.utils

import com.datastax.driver.core.Row
import com.datastax.spark.connector.cql.CassandraConnector

import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.{Duration, Seconds}
import org.apache.spark.{SparkConf, SparkContext}

trait SparkUtils {

  def createSparkContext(): SparkContext = {
    val sparkConf = new SparkConf()
      .setAppName("hiregooddevs-analysis")
      .setMaster("local[*]")
    new SparkContext(sparkConf)
  }

  def createStreamingContext(batchDurationSeconds: Int): StreamingContext = {
    val sc = SparkContext.getOrCreate()
    val duration = Seconds(batchDurationSeconds)
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
