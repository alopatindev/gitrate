package gitrate.analysis.github

import org.scalatest.concurrent.Eventually
import org.scalatest.{fixture, Outcome}

class GithubReceiverSuite extends fixture.WordSpec with Eventually {

  import gitrate.utils.HttpClientFactory.Headers

  import java.net.URL
  import java.util.concurrent.atomic.AtomicInteger
  import java.util.concurrent.ConcurrentLinkedQueue

  import play.api.libs.json.{Json, JsValue}

  import scala.io.Source

  "GithubReceiver" can {

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

    def fakeOnLoadQueries(): Seq[GithubSearchQuery] = {
      queriesReloads.incrementAndGet()
      Seq(
        GithubSearchQuery("FIRST_QUERY", "filename", 0, 0, 0, 0, ""),
        GithubSearchQuery("INVALID_QUERY", "", 0, 0, 0, 0, "")
      )
    }

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

    val fakeConf = GithubConf("", 0, 0, 0, 0)
    val receiver = new GithubReceiver(fakeConf)(fakeHttpPostBlocking, fakeOnLoadQueries, fakeOnStoreResult)

    FixtureParam(receiver, requests, responses, queriesReloads)
  }

  private val firstResponse = loadResource("GithubFirstPageFixture.json")
  private val secondResponse = loadResource("GithubLastPageFixture.json")
  private val errorResponse = loadResource("GithubErrorFixture.json")

  // TODO: move to utils

  type ConcurrentQueue = ConcurrentLinkedQueue[String]

  implicit class ConcurrentQueueUtils(queue: ConcurrentQueue) {
    import scala.collection.JavaConverters._

    def count(pattern: String): Int =
      queue.iterator.asScala.count { _ contains pattern }
  }

  private def loadResource(filename: String): JsValue = {
    val text = Source
      .fromFile(s"src/test/resources/$filename")
      .mkString
    Json.parse(text)
  }

}
