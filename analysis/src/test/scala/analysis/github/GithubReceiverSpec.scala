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

import controllers.GithubController.GithubSearchQuery
import testing.TestUtils
import utils.HttpClientFactory.Headers

import com.typesafe.config.ConfigFactory
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import org.scalatest.concurrent.Eventually
import org.scalatest.{Outcome, fixture}
import play.api.libs.json.{Json, JsValue}

class GithubReceiverSpec extends fixture.WordSpec with Eventually with TestUtils {

  "store" should {
    "be called when new users were found" in { fixture =>
      eventually {
        val usersReceived = fixture.responses.count("Inspq") >= 1
        assert(usersReceived)
      }
    }
  }

  "onStart" should {

    "execute first query" in { fixture =>
      eventually {
        val querySent = fixture.requests.count("FIRST_QUERY") >= 1
        assert(querySent)
      }
    }

    "reload queries after traversing all pages of the last query" in { fixture =>
      eventually {
        val queriesReloads = fixture.queriesReloads.get()
        val firstRequests = fixture.requests.count("FIRST_QUERY")
        assert(queriesReloads >= 10 && queriesReloads < firstRequests)
      }
    }

    "visit all pages of a query" in { fixture =>
      eventually {
        val multipleFirstPageRequests = fixture.requests.count("FIRST_QUERY") >= 2
        val secondPageRequested = fixture.requests.count("SECOND_PAGE") >= 1
        val lastPageVisited = fixture.responses.count("\"hasNextPage\":false") >= 1
        assert(multipleFirstPageRequests && secondPageRequested && lastPageVisited)
      }
    }

    "ignore error responses" in { fixture =>
      eventually {
        val multipleInvalidRequests = fixture.requests.count("INVALID_QUERY") >= 10
        val invalidResponses = fixture.responses.count("INVALID_PAGE") > 0
        assert(multipleInvalidRequests && !invalidResponses)
      }
    }
  }

  override def withFixture(test: OneArgTest): Outcome = {
    val theFixture = createFixture()
    try {
      theFixture.receiver.onStart()
      withFixture(test.toNoArgTest(theFixture))
    } finally {
      theFixture.receiver.onStop()
    }
  }

  case class FixtureParam(receiver: GithubReceiver,
                          requests: ConcurrentQueue,
                          responses: ConcurrentQueue,
                          queriesReloads: AtomicInteger)

  def createFixture(): FixtureParam = {
    val requests = new ConcurrentQueue()
    val responses = new ConcurrentQueue()
    val queriesReloads = new AtomicInteger(0)

    def fakeOnLoadQueries(): (Seq[GithubSearchQuery], Int) = {
      queriesReloads.incrementAndGet()
      val queries = Seq(
        GithubSearchQuery("FIRST_QUERY", "", 0, 0, 0, 0, ""),
        GithubSearchQuery("INVALID_QUERY", "", 0, 0, 0, 0, "")
      )
      val queryIndex = -1
      (queries, queryIndex)
    }

    def fakeSaveReceiverState(queryIndex: Int): Unit = ()

    val firstResponse = loadJsonResource("/github/FirstPageFixture.json")
    val secondResponse = loadJsonResource("/github/LastPageFixture.json")
    val errorResponse = loadJsonResource("/github/ErrorFixture.json")

    def stubHttpGetBlocking(url: URL, headers: Headers): JsValue = Json.parse("{}")

    def fakeHttpPostBlocking(url: URL, data: JsValue, headers: Headers): JsValue = {
      val dataString = data.toString
      requests.add(dataString)
      dataString match {
        case d: String if d contains "SECOND_PAGE"   => secondResponse
        case d: String if d contains "INVALID_QUERY" => errorResponse
        case _                                       => firstResponse
      }
    }

    // weird design decision forces us to do this
    // alternatively some mocking framework could be used
    def fakeOnStoreResult(receiver: GithubReceiver, result: String): Unit = {
      val _ = responses.add(result)
    }

    val fakeConf = GithubConf(
      ConfigFactory.load("github/GithubReceiverFixture.conf"),
      httpGetBlocking = stubHttpGetBlocking,
      httpPostBlocking = fakeHttpPostBlocking
    )

    val receiver = new GithubReceiver(fakeConf, fakeOnLoadQueries, fakeSaveReceiverState, fakeOnStoreResult)

    FixtureParam(receiver, requests, responses, queriesReloads)
  }

}
