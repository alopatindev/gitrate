package controllers

import controllers.GithubController.{AnalyzedRepository, GithubSearchQuery}
import testing.PostgresTestUtils

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlAction

class GithubControllerSpec extends PostgresTestUtils {

  "loadAnalyzedRepositories" should {

    "load analyzed repositories" in { _ =>
      val repoIdsBase64: Seq[String] = Seq("repo1")
      val results: Seq[AnalyzedRepository] = GithubController.loadAnalyzedRepositories(repoIdsBase64).collect()
      assert(results.length === 1)
      assert(results.head.idBase64 === repoIdsBase64.head)
    }

  }

  "loadQueries" should {

    "load queries" in { _ =>
      val (results: Seq[GithubSearchQuery], _) = GithubController.loadQueries()
      val expected = GithubSearchQuery(language = "C++",
                                       filename = ".travis.yml",
                                       minRepoSizeKiB = 10,
                                       maxRepoSizeKiB = 2048,
                                       minStars = 0,
                                       maxStars = 100,
                                       pattern = "")
      assert(results contains expected)
    }

  }

  "saveReceiverState" should {

    "save query index" in { _ =>
      val expected = 123

      val result = GithubController.saveReceiverState(queryIndex = expected).map { _ =>
        val (_, queryIndex) = GithubController.loadQueries()
        queryIndex
      }

      assert(Await.result(result, Duration.Inf) === expected)
    }

  }

  override def initialData: SqlAction[Int, NoStream, Effect] = sqlu"""
    INSERT INTO users (
      id,
      github_user_id,
      github_login,
      full_name,
      updated_by_user
    ) VALUES (DEFAULT, 1, 'fake-user', 'fake-user', DEFAULT);

    INSERT INTO repositories (
      id,
      raw_id,
      user_id,
      name,
      lines_of_code,
      updated_by_analyzer
    ) VALUES
      (DEFAULT, 'repo1', 1, 'test_repo1', 1000, DEFAULT),
      (DEFAULT, 'repo2', 1, 'test_repo2', 2000, DEFAULT)"""

}
