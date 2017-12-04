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

package analysis.github

import controllers.GithubController.AnalyzedRepository
import testing.TestUtils
import utils.HttpClientFactory.Headers

import com.holdenkarau.spark.testing.DataFrameSuiteBase
import com.typesafe.config.ConfigFactory
import java.net.URL
import java.util.Calendar
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.types.TimestampType
import org.scalatest.{Outcome, fixture}
import org.scalatest.tagobjects.Slow
import play.api.libs.json.{JsValue, Json}

class GithubExtractorSpec extends fixture.WordSpec with DataFrameSuiteBase with TestUtils {

  "filter GitHub API output" should {

    "not ignore target users" taggedAs Slow in { fixture =>
      assert(fixture containsUser "target-user")
    }

    "ignore users with invalid type (e.g. organization)" taggedAs Slow in { fixture =>
      assert(!(fixture containsUser "organization-user"))
    }

    "not ignore target repositories" taggedAs Slow in { fixture =>
      assert(fixture.userHasRepo("target-user", "repo-pinned1"))
      assert(fixture.userHasRepo("target-user", "repo-pinned2"))
    }

    "ignore too young repositories" taggedAs Slow in { fixture =>
      assert(!fixture.userHasRepo("target-user", "YOUNG-repo"))
      assert(!fixture.userHasRepo("hermityang", "YOUNG-repo-2"))
    }

    "ignore forked repositories" taggedAs Slow in { fixture =>
      assert(!fixture.userHasRepo("target-user", "forked-repo"))
    }

    "ignore mirrored repositories" taggedAs Slow in { fixture =>
      assert(!fixture.userHasRepo("target-user", "mirrored-repo"))
    }

    "ignore repositories with primary language we don't support" taggedAs Slow in { fixture =>
      assert(!fixture.userHasRepo("target-user", "repo-with-UNKNOWN-as-primary"))
    }

    "ignore duplicated repositories" taggedAs Slow in { fixture =>
      val repositories = fixture.repositoriesOfUser("target-user")
      assert(repositories.toSet.size === repositories.size)
    }

    "ignore users with too little number of target repositories" taggedAs Slow in { fixture =>
      assert(!(fixture containsUser "user-with-a-single-repo"))
    }

    "ignore recently analyzed repositories" taggedAs Slow in { fixture =>
      assert(!fixture.userHasRepo("target-user", "recently-analyzed-repo"))
    }

    "allow only repositories with commits mostly made by the user" taggedAs Slow in { fixture =>
      assert(!fixture.userHasRepo("target-user", "repo-with-mostly-commits-by-OTHERS"))
    }

    "get user details" taggedAs Slow in { fixture =>
      fixture
        .findUser("target-user")
        .foreach(user => {
          assert(user.fullName === Some("First Last"))
          assert(user.description === Some("User Description"))
          assert(user.company === Some("Company Name"))
          assert(user.location === Some("City, Country"))
          assert(user.email === None)
          assert(user.blog === None)
          assert(user.jobSeeker === Some(true))
        })
    }

  }

  override def withFixture(test: OneArgTest): Outcome = {
    val userResponse: JsValue = loadJsonResource("/github/UserDetailsFixture.json")

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
      ConfigFactory.load("github/GithubExtractorFixture.conf"),
      httpGetBlocking = fakeHttpGetBlocking,
      httpPostBlocking = stubHttpPostBlocking
    )

    val githubExtractor = new GithubExtractor(conf, loadAnalyzedRepositories)

    val inputJsValue: JsValue = (loadJsonResource("/github/GithubExtractorFixture.json") \ "data" \ "search").get
    val input: Seq[String] = Seq(inputJsValue.toString)

    val theFixture = FixtureParam(githubExtractor, input)
    try {
      withFixture(test.toNoArgTest(theFixture))
    } finally {}
  }

  case class FixtureParam(githubExtractor: GithubExtractor, input: Seq[String]) {
    val users: Iterable[GithubUser] = githubExtractor
      .parseAndFilterUsers(sc.parallelize(input))

    def repositories: Iterable[GithubRepository] = users.flatMap(u => u.repositories)

    def containsUser(login: String): Boolean = findUsers(login).nonEmpty

    def userHasRepo(login: String, repoName: String): Boolean = findRepositories(login, repoName).nonEmpty

    def repositoriesOfUser(login: String): Iterable[String] =
      findUsers(login).flatMap(user => user.repositories.map(repo => repo.name))

    def findUsers(login: String): Iterable[GithubUser] = users.filter(_.login == login)

    def findUser(login: String): Option[GithubUser] = findUsers(login).headOption

    def findRepositories(login: String, repoName: String): Iterable[GithubRepository] =
      findUsers(login).flatMap(user => user.repositories.filter(repo => repo.name == repoName))
  }

  private def loadAnalyzedRepositories(repoIdsBase64: Seq[String]): Dataset[AnalyzedRepository] = {
    import spark.implicits._

    val currentDate = (Calendar.getInstance().getTimeInMillis / 1000L).toString
    val oldDate = "0"
    spark.read
      .json(
        sc.parallelize(Seq(
            s"""{
  "raw_id": "MDEwOlJlcG9zaXRvcnk4MTQyMTAyCg==",
  "updated_by_analyzer": $currentDate
}""",
            s"""
{
  "raw_id": "MDEwOlJlcG9zaXRvcnkyMDg5Mjg2MA==",
  "updated_by_analyzer": $oldDate
}
"""
          ))
          .toDS)
      .select('raw_id as "idBase64", 'updated_by_analyzer.cast(TimestampType) as "updatedByAnalyzer")
      .as[AnalyzedRepository]
  }

}
