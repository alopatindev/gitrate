package controllers

import controllers.GithubController.GithubSearchQuery
import org.scalatest.{Outcome, fixture}

class GithubControllerSuite extends fixture.WordSpec {

  import scala.concurrent.Await
  import scala.concurrent.duration.Duration
  import slick.jdbc.PostgresProfile.api._

  "GithubControllerSuite" can {

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

  case class FixtureParam()

  override def withFixture(test: OneArgTest): Outcome = { // scalastyle:ignore
    val username = "gitrate_test" // TODO: move to a common place?
    val database = username
    val db: Database =
      Database.forURL(url = s"jdbc:postgresql:$database", user = username, driver = "org.postgresql.Driver")

    val schema = sqlu"""
      CREATE TABLE IF NOT EXISTS languages (
        id SERIAL PRIMARY KEY,
        language TEXT UNIQUE NOT NULL
      );

      INSERT INTO languages (id, language) VALUES
        (DEFAULT, 'JavaScript'),
        (DEFAULT, 'C++'),
        (DEFAULT, 'C');

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

    val initialData = sqlu"""
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
  (DEFAULT, (SELECT id FROM javascript_language), '.eslintrc.*', 10, 2048, 0, 100, '', TRUE);"""

    val dropData = sqlu"DROP OWNED BY gitrate_test" // FIXME: is there a correct way to unhardcode username here?
    val result = db.run(DBIO.seq(dropData, schema, initialData).transactionally)
    Await.result(result, Duration.Inf)

    val theFixture = FixtureParam()
    try {
      withFixture(test.toNoArgTest(theFixture))
    } finally {
      db.close()
    }
  }
}
