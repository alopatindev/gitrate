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

package controllers

import utils.{AppConfig, SlickUtils, SparkUtils}

import org.apache.spark.sql.Dataset
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.util.Try
import slick.jdbc.PostgresProfile.api._

object GithubController extends AppConfig with SlickUtils with SparkUtils {

  def loadAnalyzedRepositories(repoIdsBase64: Seq[String]): Dataset[AnalyzedRepository] = {
    val sparkSession = getOrCreateSparkSession()
    import sparkSession.implicits._

    Postgres
      .getTable("repositories")
      .select('raw_id as "idBase64", 'updated_by_analyzer as "updatedByAnalyzer")
      .filter('idBase64.isin(repoIdsBase64: _*))
      .as[AnalyzedRepository]
  }

  def loadQueries(): (Seq[GithubSearchQuery], Int) = {
    val queriesFuture: Future[Vector[GithubSearchQuery]] = runQuery(loadQueriesQuery)
      .map(results => results.map(args => GithubSearchQuery.tupled(args)))

    val defaultQueryIndex = -1
    val defaultResult = (Vector.empty, defaultQueryIndex)
    val queryIndexFuture: Future[Int] = runQuery(loadReceiverStateQuery)
      .map(_.headOption.map(_.toInt).getOrElse(defaultQueryIndex))

    val result = Try(Await.result(for {
      queries <- queriesFuture
      queryIndex <- queryIndexFuture
    } yield (queries, queryIndex), timeout))

    result.getOrElse(defaultResult)
  }

  def saveReceiverState(queryIndex: Int): Future[Unit] = {
    val query = sqlu"""
      UPDATE github_receiver_state
      SET value = ${queryIndex.toString}
      WHERE key = 'query_index'""".transactionally

    val future = runQuery(query).map(_ => ())
    future.foreach(_ => logInfo(s"finished saving query index: $queryIndex"))
    future
  }

  case class AnalyzedRepository(idBase64: String, updatedByAnalyzer: java.sql.Timestamp)

  case class GithubSearchQuery(language: String,
                               filename: String,
                               minRepoSizeKiB: Int,
                               maxRepoSizeKiB: Int,
                               minStars: Int,
                               maxStars: Int,
                               pattern: String) {

    val sort = "updated"
    val fork = false
    val mirror = false

    override def toString: String =
      s"language:$language in:$filename sort:$sort mirror:$mirror fork:$fork " +
        s"size:$minRepoSizeKiB..$maxRepoSizeKiB stars:$minStars..$maxStars $pattern"

  }

  type T = (String, String, Int, Int, Int, Int, String)
  private[this] val loadQueriesQuery = sql"""
SELECT
  languages.language AS language,
  github_search_queries.filename,
  github_search_queries.min_repo_size_kib AS minRepoSizeKiB,
  github_search_queries.max_repo_size_kib AS maxRepoSizeKiB,
  github_search_queries.min_stars AS minStars,
  github_search_queries.max_stars AS maxStars,
  github_search_queries.pattern
FROM github_search_queries
INNER JOIN languages ON languages.id = github_search_queries.language_id
WHERE enabled = true""".as[T]

  private[this] val loadReceiverStateQuery = sql"""
    SELECT value
    FROM github_receiver_state
    WHERE key = 'query_index'
    LIMIT 1""".as[String]

  private val timeout: FiniteDuration = 10 seconds

}
