// Copyright 2017 Alexander Lopatin
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package utils

import SparkUtils.DurationUtils

import com.typesafe.config.Config
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Dataset, Row, SparkSession}
import org.apache.spark.streaming.{Duration, Milliseconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}

trait SparkUtils {

  def appConfig: Config
  def postgresqlConfig: Config

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

  def initializeSpark(): Unit = {
    val _ = (getOrCreateSparkContext(), getOrCreateSparkSession())
  }

  object Postgres {

    private val dbOptions = Seq("url", "user", "driver")
      .map(key => key -> postgresqlConfig.getString(key))
      .toMap

    def getTable(table: String): Dataset[Row] =
      getOrCreateSparkSession().read
        .format("jdbc")
        .options(dbOptions + ("dbtable" -> table))
        .load

    def executeSQL(query: String): Dataset[Row] = getTable(s"""($query) AS tmp""")

  }

  private val batchDuration: Duration = appConfig.getDuration("stream.batchDuration").toSparkDuration

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

  implicit class DurationUtils(duration: java.time.Duration) {

    def toSparkDuration: Duration = Milliseconds(duration.toMillis)

  }

}
