package controllers

import controllers.GithubController.{AnalyzedRepository, GithubSearchQuery}
import testing.PostgresTestUtils
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlAction

class GithubControllerSuite extends PostgresTestUtils {

  "GithubControllerSuite" can {

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
        val results: Seq[GithubSearchQuery] = GithubController.loadQueries()
        assert(results.length === 2)
        assert(
          results contains GithubSearchQuery(language = "C++",
                                             filename = ".travis.yml",
                                             minRepoSizeKiB = 10,
                                             maxRepoSizeKiB = 2048,
                                             minStars = 0,
                                             maxStars = 100,
                                             pattern = "hello"))
      }

    }

  }

  def schema: SqlAction[Int, NoStream, Effect] = sqlu"""
    CREATE TABLE IF NOT EXISTS languages (
      id SERIAL PRIMARY KEY,
      language TEXT UNIQUE NOT NULL
    );

    INSERT INTO languages (id, language) VALUES
      (DEFAULT, 'JavaScript'),
      (DEFAULT, 'C++');

    CREATE TABLE IF NOT EXISTS repositories (
      id SERIAL PRIMARY KEY,
      raw_id TEXT UNIQUE NOT NULL,
      fake_user_id INTEGER NOT NULL,
      name TEXT NOT NULL,
      lines_of_code INTEGER NOT NULL,
      updated_by_analyzer TIMESTAMP DEFAULT NOW() NOT NULL
    );

    CREATE TABLE IF NOT EXISTS github_search_queries (
      id SERIAL PRIMARY KEY,
      language_id INTEGER REFERENCES languages NOT NULL,
      filename TEXT NOT NULL,
      min_repo_size_kib INT NOT NULL,
      max_repo_size_kib INT NOT NULL,
      min_stars INT NOT NULL,
      max_stars INT NOT NULL,
      pattern TEXT NOT NULL,
      enabled BOOLEAN NOT NULL
    )"""

  def initialData: SqlAction[Int, NoStream, Effect] = sqlu"""
    INSERT INTO repositories (
      id,
      raw_id,
      fake_user_id,
      name,
      lines_of_code,
      updated_by_analyzer
    ) VALUES
      (DEFAULT, 'repo1', 0, 'test_repo1', 1000, DEFAULT),
      (DEFAULT, 'repo2', 0, 'test_repo2', 2000, DEFAULT);

    WITH
      javascript_language AS (SELECT id FROM languages WHERE language = 'JavaScript'),
      cpp_language AS (SELECT id FROM languages WHERE language = 'C++')
    INSERT INTO github_search_queries (
      id,
      language_id,
      filename,
      min_repo_size_kib,
      max_repo_size_kib,
      min_stars,
      max_stars,
      pattern,
      enabled
    ) VALUES
      (DEFAULT, (SELECT id FROM cpp_language), '.travis.yml', 10, 2048, 0, 100, 'hello', TRUE),
      (DEFAULT, (SELECT id FROM javascript_language), '.eslintrc.*', 10, 2048, 0, 100, '', TRUE)"""

}
