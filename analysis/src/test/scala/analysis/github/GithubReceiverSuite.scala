package hiregooddevs.analysis.github

import org.scalatest.concurrent.Eventually
import org.scalatest.Matchers._
import org.scalatest.{BeforeAndAfter, WordSpec}

class GithubReceiverSuite extends WordSpec with BeforeAndAfter with Eventually {

  import java.io.{ByteArrayInputStream, File, InputStream}

  import scala.concurrent.duration._
  import scala.collection.mutable
  import scala.io.Source

  var fixture: Option[Fixture] = None

  before {
    fixture = {
      val f = new Fixture
      f.receiver.onStart
      Some(f)
    }
  }

  after {
    fixture.foreach { f =>
      f.receiver.onStop()
      fixture = None
    }
  }

  "GithubReceiver" can {

    "store" should {
      "be called when new users were found" in {
        eventually(timeout(1 second)) {
          val usersReceived = fixture
            .map(_.countResponses("Inspq") >= 1)
            .getOrElse(false)
          assert(usersReceived)
        }
      }
    }

    "onStart" should {
      "execute first query" in {
        eventually {
          val querySent = fixture
            .map(_.countRequests("eslintrc") >= 1)
            .getOrElse(false)
          assert(querySent)
        }
      }
      "execute first query again, after traversing all pages of the last query" in {
        eventually {
          val multipleFirstPageRequests = fixture
            .map(_.countRequests("eslintrc") >= 2)
            .getOrElse(false)
          val secondPageRequested = fixture
            .map(_.countRequests("second page") >= 1)
            .getOrElse(false)
          val lastPageVisited = fixture
            .map(_.countResponses("\"hasNextPage\":false") >= 1)
            .getOrElse(false)
          assert(multipleFirstPageRequests && secondPageRequested && lastPageVisited)
        }
      }
      "ignore error responses" in {
        eventually {
          val multipleInvalidRequests = fixture
            .map(_.countRequests("invalid") >= 2)
            .getOrElse(false)
          val invalidResponses = fixture
            .map(_.countResponses("INVALID_CURSOR_ARGUMENTS") > 0)
            .getOrElse(false)
          assert(multipleInvalidRequests && !invalidResponses)
        }
      }
    }

  }

  class Fixture {

    def countRequests(pattern: String): Int =
      _requests.filter { _ contains pattern }.length

    def countResponses(pattern: String): Int =
      _responses.filter { _ contains pattern }.length

    val receiver = {
      val conf =
        GithubConf(apiToken = "", maxResults = 0, maxRepositories = 0, maxPinnedRepositories = 0, maxLanguages = 0)
      val queries = Seq(
        GithubSearchQuery(language = "JavaScript", filename = ".eslintrc.*", minRepoSizeKiB = 1, maxRepoSizeKiB = 2),
        GithubSearchQuery(language = "JavaScript", filename = ".travis.yml", minRepoSizeKiB = 1, maxRepoSizeKiB = 2),
        GithubSearchQuery(language = "invalid", filename = "invalid", minRepoSizeKiB = -1, maxRepoSizeKiB = -1)
      )
      new FakeReceiver(conf, queries) with FakeHttpClient
    }

    private val firstResponse = loadResource("GithubFirstPageFixture.json")
    private val secondResponse = loadResource("GithubLastPageFixture.json")
    private val errorResponse = loadResource("GithubErrorFixture.json")

    private def loadResource(filename: String): String =
      Source
        .fromFile(s"src/test/resources/$filename")
        .mkString

    abstract class FakeReceiver(conf: GithubConf, queries: => Seq[GithubSearchQuery])
        extends GithubReceiver(conf, queries) {
      override def store(response: String): Unit = {
        _responses = response :: _responses
      }
    }

    @volatile private var _requests: List[String] = List.empty
    @volatile private var _responses: List[String] = List.empty

    trait FakeHttpClient {
      def httpPostBlocking(url: String, data: String, headers: Map[String, String], timeout: Duration): InputStream = {
        _requests = data :: _requests
        val response = data match {
          case d if d contains "second page" => secondResponse
          case d if d contains "invalid"     => errorResponse
          case _                             => firstResponse
        }

        new ByteArrayInputStream(response.getBytes("UTF-8"))
      }
    }

  }

}
