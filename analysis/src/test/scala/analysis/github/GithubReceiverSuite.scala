package hiregooddevs.github

import org.scalatest.Matchers._
import org.scalatest.WordSpec

class GithubReceiverSuite extends WordSpec {

  "GithubReceiver" can {

    "store" should {
      "be called when new users were found" in {} // hard
    }

    "loadQueries" should {
      "be called after onStart" in {} // easy
      "be called after traversing all pages of current query" in {} // middle
      "return old queries, when can't load them" in {} // hard
    }

    "loadState" should {
      "be called after onStart" in {} // easy
    }

    "storeState" should {
      "be called after processing each page" in {} // middle
    }

    "getState" should {
      "be initially unset" in {
        //val receiver = new GithubReceiver(apiToken = "dummy")
        //receiver.getState shouldEqual None
      }
      "contain the first query after onStart" in {} // middle
      "contain the next query when previous ran out of pages" in {} // middle
      "contain the same query as before interruption" in {} // hard
      "contain the same page as before interruption" in {} // hard
      "contain the first of the same query as before interruption when page has become invalid" in {} // hard
      "contain the first query and page if receiver was interrupted on a query that no longer exists" in {} // hard
    }

    "onStop" should {
      "interrupt current https connection" in {} // hard
    }

  }

}
