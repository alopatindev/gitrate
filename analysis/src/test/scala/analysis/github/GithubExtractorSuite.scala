package gitrate.analysis.github

import gitrate.utils.TestUtils

import com.holdenkarau.spark.testing.DataFrameSuiteBase
import org.scalatest.{fixture, Outcome}

class GithubExtractorSuite extends fixture.WordSpec with DataFrameSuiteBase with TestUtils {

  import com.typesafe.config.ConfigFactory

  import java.net.URL
  import java.util.Calendar

  import play.api.libs.json.{JsValue, Json}

  import gitrate.analysis.github.GithubConf
  import gitrate.utils.HttpClientFactory.Headers

  import org.apache.log4j.{Level, Logger}
  import org.apache.spark.sql.{Dataset, Row}
  import org.apache.spark.sql.types.TimestampType

  Logger.getLogger("org.apache.spark").setLevel(Level.ERROR)

  "GithubUsersParser" can {

    "filter GitHub API output" should {

      "not ignore target users" in { fixture =>
        assert(fixture containsUser "target-user")
      }

      "ignore users with invalid type (e.g. organization)" in { fixture =>
        assert(!(fixture containsUser "organization-user"))
      }

      "not ignore target repositories" in { fixture =>
        assert(fixture.userHasRepo("target-user", "repo-pinned1"))
        assert(fixture.userHasRepo("target-user", "repo-pinned2"))
      }

      "ignore too young repositories" in { fixture =>
        assert(!fixture.userHasRepo("target-user", "YOUNG-repo"))
        assert(!fixture.userHasRepo("hermityang", "YOUNG-repo-2"))
      }

      "ignore forked repositories" in { fixture =>
        assert(!fixture.userHasRepo("target-user", "forked-repo"))
      }

      "ignore mirrored repositories" in { fixture =>
        assert(!fixture.userHasRepo("target-user", "mirrored-repo"))
      }

      "ignore repositories with primary language we don't support" in { fixture =>
        assert(!fixture.userHasRepo("target-user", "repo-with-UNKNOWN-as-primary"))
      }

      "ignore duplicated repositories" in { fixture =>
        val repositories = fixture.repositoriesOfUser("target-user")
        assert(repositories.toSet.size === repositories.size)
      }

      "ignore users with too little number of target repositories" in { fixture =>
        assert(!(fixture containsUser "user-with-a-single-repo"))
      }

      "ignore recently analyzed repositories" in { fixture =>
        assert(!fixture.userHasRepo("target-user", "recently-analyzed-repo"))
      }

      "allow only repositories with commits mostly made by the user" in { fixture =>
        assert(!fixture.userHasRepo("target-user", "repo-with-mostly-commits-by-OTHERS"))
      }

      "get user details" in { fixture =>
        fixture
          .findUser("target-user")
          .foreach(user => {
            assert(user.fullName === Some("First Last"))
            assert(user.description === Some("User Description"))
            assert(user.company === Some("Company Name"))
            assert(user.location === Some("City, Country"))
            assert(user.email === Some("valid-mail@domain.com"))
            assert(user.blog === Some("https://target-user.github.io"))
            assert(user.jobSeeker === Some(true))
          })
      }

    }

    // TODO: separate module?
    /*"fetch additional info" should {
      "detect services used" in {
        assert(
          fixture.servicesOf("alopatindev", "qdevicemonitor") === Seq("travis-ci.org", "appveyor.com")
          fixture.servicesOf("alopatindev", "find-telegram-bot") === Seq(
            "travis-ci.org",
            "codecov.io",
            "codeclimate.com",
            "semaphoreci.com",
            "bithound.io",
            "versioneye.com",
            "david-dm.org",
            "dependencyci.com",
            "snyk.io",
            "npmjs.com"
          ))
      }
    }

    // TODO: separate module per language?
    "external containerized program" should {
      "download repo" in {
        fixture.downloadRepo("alopatindev", "find-telegram-bot")
        eventually {
          assert(fixture.fileExists("/tmp/gitrate-analyzer/alopatindev/find-telegram-bot/.gitignore"))
        }
      }
      "detect dependencies" in {
        fixture.downloadRepo("alopatindev", "find-telegram-bot")
        eventually {
          assert(fixture.rawDependenciesOf("alopatindev", "find-telegram-bot") === Seq("phantom", "telegraf", "winston", "bithound", "codecov", "eslint", "eslint-plugin-better", "eslint-plugin-mocha", "eslint-plugin-private-props", "eslint-plugin-promise", "istanbul", "mocha", "mocha-logger", "nodemon"))
        }
      }
      "rename dependencies and ignore aliases" in {
        assert(fixture.dependenciesOf("alopatindev", "find-telegram-bot") contains "PhantomJS")
        assert(!(fixture.dependenciesOf("alopatindev", "find-telegram-bot") contains "phantomjs"))
      }
      "cleanup temporary files when done" in {
        fixture.cleanup("alopatindev", "find-telegram-bot")
        eventually {
          assert(!fixture.fileExists("/tmp/gitrate-analyzer/alopatindev/find-telegram-bot/.gitignore"))
        }
      }
    }

    // TODO: separate module?
    "static analysis" should {
      "apply analysis of supported languages used in the repo" in { ??? }
      "run on the same machine and container as wget" in { ??? }
      "return bad grades when code is bad" in { ??? }
      "return good grades when code is good" in { ??? }
      //"return code coverage grade" in { ??? }
      "return all supported grade types" in { ??? }
      //"ignore code that can't compile" in { ??? } // it can fail because of dependencies we don't have
      "ignore users with too low total grade" in { ??? }
    }*/

  }

  override def withFixture(test: OneArgTest): Outcome = {
    val userResponse: JsValue = loadJsonResource("/GithubUserDetailsFixture.json")

    def fakeHttpGetBlocking(url: URL, headers: Headers): JsValue = {
      val urlString = url.toString
      if (urlString contains "/repos/") {
        Json.parse(if (urlString contains "/target-user/repo-with-mostly-commits-by-OTHERS/stats/participation") {
          """{ "all": [1, 9], "owner": [0, 1] }"""
        } else {
          """{ "all": [1, 9], "owner": [0, 8] }"""
        })
      } else if (urlString contains "/user/") {
        userResponse
      } else {
        Json.parse("{}")
      }
    }

    def stubHttpPostBlocking(url: URL, data: JsValue, headers: Headers): JsValue = Json.parse("{}")

    val conf = GithubConf(
      ConfigFactory.load("GithubExtractorFixture.conf"),
      httpGetBlocking = fakeHttpGetBlocking,
      httpPostBlocking = stubHttpPostBlocking
    )

    import spark.implicits._

    val currentDate = (Calendar.getInstance().getTimeInMillis() / 1000L).toString
    val oldDate = "0"
    val currentRepositories: Dataset[Row] = spark.read
      .json(
        sc.parallelize(Seq(
          s"""{
  "raw_id": "MDEwOlJlcG9zaXRvcnk4MTQyMTAyCg==",
  "updated_by_analyzer": ${currentDate}
}""",
          s"""
{
  "raw_id": "MDEwOlJlcG9zaXRvcnkyMDg5Mjg2MA==",
  "updated_by_analyzer": ${oldDate}
}
"""
        )))
      .select($"raw_id", ($"updated_by_analyzer".cast(TimestampType)) as "updated_by_analyzer")

    val githubExtractor = new GithubExtractor(conf, currentRepositories)

    val inputJsValue: JsValue = (loadJsonResource("/GithubExtractorFixture.json") \ "data" \ "search").get
    val input: Seq[String] = Seq(inputJsValue.toString)

    val theFixture = FixtureParam(githubExtractor, input)
    try {
      withFixture(test.toNoArgTest(theFixture))
    } finally {}
  }

  case class FixtureParam(val githubExtractor: GithubExtractor, val input: Seq[String]) {
    val users: Iterable[GithubUser] = githubExtractor
      .parseAndFilterUsers(sc.parallelize(input))

    def repositories: Iterable[GithubRepo] = users.flatMap(u => u.repositories)

    def containsUser(login: String): Boolean = !findUsers(login).isEmpty

    def userHasRepo(login: String, repoName: String): Boolean = !findRepositories(login, repoName).isEmpty

    def repositoriesOfUser(login: String): Iterable[String] =
      findUsers(login).flatMap(user => user.repositories.map(repo => repo.name))

    def findUsers(login: String): Iterable[GithubUser] = users.filter(_.login == login)

    def findUser(login: String): Option[GithubUser] = findUsers(login).headOption

    def findRepositories(login: String, repoName: String): Iterable[GithubRepo] =
      findUsers(login).flatMap(user => user.repositories.filter(repo => repo.name == repoName))
  }

}
