package controllers

import utils.{LogUtils, SlickUtils}

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlStreamingAction

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

object GithubController extends SlickUtils with LogUtils {

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
  private[this] val query: SqlStreamingAction[Vector[T], T, Effect] = sql"""
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

  def loadQueries(): Seq[GithubSearchQuery] = {
    val future: Future[Vector[GithubSearchQuery]] = runQuery(query)
      .map(results => results.map(args => GithubSearchQuery.tupled(args)))
    Try(Await.result(future, timeout)).getOrElse(Seq.empty)
  }

  private val timeout: FiniteDuration = 10 seconds

}
