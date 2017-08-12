package hiregooddevs.analysis

import github._

import org.apache.log4j.{Level, Logger}

import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}

import org.apache.spark.streaming._
import org.apache.spark.streaming.StreamingContext._
//import org.apache.spark.streaming.receiver.Receiver

//import org.apache.spark.streaming.dstream.ReceiverInputDStream

//import com.datastax.spark.connector._
//import com.datastax.spark.connector.streaming._

object Main {

  val conf = new SparkConf()
    .setAppName("FindGithubUsers") // TODO: use class name
    .setMaster("local[*]") // TODO: use config
    .set("spark.cleaner.ttl", "3600")
  //.setJars()

  def main(args: Array[String]): Unit = {
    val sc = new SparkContext(conf)
    val ssc = new StreamingContext(sc, Seconds(3))

    // FIXME: move to config?
    /*Logger.getRootLogger().setLevel(Level.OFF)
    Logger.getLogger("org").setLevel(Level.OFF)
    Logger.getLogger("akka").setLevel(Level.OFF)*/

    val stream = ssc.receiverStream(new GithubReceiver(apiToken = "")) // TODO: move to config

    stream.print()
//
//  stream
//    .map(status => status.getText)
//  //.foreachRDD { rdd => }
//  //.flatMap(record => record.split(" "))
//  //.map(word => word -> 1)
//  //.reduceByKey(_ + _)
//  //.print()
//
    ssc.start()
    ssc.awaitTermination()

    sc.stop()
  }

}

// private class SearchReceiver extends Receiver {}
