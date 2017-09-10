package gitrate.analysis.github.parser

import gitrate.utils.TestUtils
import org.scalatest.{fixture, Outcome}

class GithubParserSuite extends fixture.WordSpec with TestUtils {

  import java.net.URL
  import play.api.libs.json.{JsValue, Json}

  import GithubParser.{GithubRepo, GithubUser, parseUserId}
  import gitrate.utils.HttpClientFactory.Headers

  "GithubUsersParser" can {

    "filter GitHub API output" should {

      "ignore users with invalid type (e.g. organization)" in { fixture =>
        assert(fixture containsUser "target-user")
        assert(!(fixture containsUser "organization-user"))
      }

      "not ignore target repos" in { fixture =>
        assert(fixture.userHasRepo("target-user", "repo-pinned1"))
        assert(fixture.userHasRepo("target-user", "repo-pinned2"))
      }

      "ignore too young repos" in { fixture =>
        assert(!fixture.userHasRepo("target-user", "YOUNG-repo"))
        assert(!fixture.userHasRepo("hermityang", "YOUNG-repo-2"))
      }

      "ignore forked repos" in { fixture =>
        assert(!fixture.userHasRepo("target-user", "forked-repo"))
      }

      "ignore mirrored repos" in { fixture =>
        assert(!fixture.userHasRepo("target-user", "mirrored-repo"))
      }

      "ignore repos with primary language we don't support" in { fixture =>
        assert(!fixture.userHasRepo("target-user", "repo-with-UNKNOWN-as-primary"))
      }

      "returns found repos and then pinned repos as priority" in { fixture =>
        assert(
          fixture.reposOfUser("target-user").take(4).toSeq === Seq("repo-found",
                                                                   "repo-pinned1",
                                                                   "repo-pinned2",
                                                                   "another-target-repo"))
      }

      "ignore users with too little number of target repositories" in { fixture =>
        assert(!(fixture containsUser "user-with-a-single-repo"))
      }

      "ignore recently analyzed users" ignore { fixture =>
        assert(!(fixture containsUser "user-with-a-single-repo"))
      }

      "ignore recently analyzed repos" ignore { fixture =>
        // TODO: request all repos of a page at the same time?
        assert(!fixture.userHasRepo("target-user", "recently-updated-repo"))
      }

      "allow only repos with commits mostly made by the user" in { fixture =>
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

    "package object" should {

      "parse and filter user IDs" in { fixture =>
        val user = "MDQ6VXNlcjY1MDI0MDE="
        val organization = "MDEyOk9yZ2FuaXphdGlvbjI1MDE0Mjcy"
        val invalid = "invalid"
        assert(parseUserId(user) === Some(6502401))
        assert(parseUserId(organization).isEmpty)
        assert(parseUserId(invalid).isEmpty)
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

    val reposParser = new GithubReposParser(minRepoAgeDays = 2 * 30,
                                            minOwnerToAllCommitsRatio = 0.7,
                                            supportedLanguages = Set("JavaScript", "Python"),
                                            minTargetRepos = 2,
                                            httpGetBlocking = fakeHttpGetBlocking)
    val githubParser = new GithubParser(reposParser)
    val searchResponse: JsValue = loadJsonResource("/GithubParserFixture.json")
    val input: JsValue = (searchResponse \ "data" \ "search").get
    val theFixture = FixtureParam(githubParser, input)
    try {
      withFixture(test.toNoArgTest(theFixture))
    } finally {}
  }

  case class FixtureParam(val githubParser: GithubParser, val input: JsValue) {
    val users: Seq[GithubUser] = githubParser.parseUsersAndRepos(input)

    def repos: Seq[GithubRepo] = users.flatMap(u => u.repos)

    def containsUser(login: String): Boolean = findUsers(login).isDefinedAt(0)

    def userHasRepo(login: String, repoName: String): Boolean = findRepos(login, repoName).isDefinedAt(0)

    def reposOfUser(login: String): Seq[String] =
      findUsers(login).flatMap(user => user.repos.map(repo => repo.name))

    def findUsers(login: String): Seq[GithubUser] = users.filter(_.login == login)

    def findUser(login: String): Option[GithubUser] = findUsers(login).headOption

    def findRepos(login: String, repoName: String): Seq[GithubRepo] =
      findUsers(login).flatMap(user => user.repos.filter(repo => repo.name == repoName))
  }

}
