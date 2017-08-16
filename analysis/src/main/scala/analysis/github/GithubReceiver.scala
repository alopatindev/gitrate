package hiregooddevs.analysis.github

import hiregooddevs.utils.{LogUtils, RxUtils}

import java.io.{File, InputStream}

import rx.lang.scala.Subscription

import org.apache.log4j.Level
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.receiver.Receiver

import play.api.libs.json._

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.io.Source
import scala.util.{Failure, Success, Try}

abstract class GithubReceiver(apiToken: String,
                              queries: Seq[GithubSearchQuery],
                              storageLevel: StorageLevel =
                                StorageLevel.MEMORY_AND_DISK)
    extends Receiver[String](storageLevel: StorageLevel)
    with RxUtils
    with LogUtils {

  log.setLevel(Level.DEBUG) // TODO: move to config

  def httpPostBlocking(url: String,
                       data: String,
                       headers: Map[String, String],
                       timeout: Duration): InputStream

  private val requestTimeout = 10 seconds
  private val apiURL = "https://api.github.com/graphql"
  private val queryTemplate: String = resourceToString("/GithubSearch.graphql")

  private val responsesObs = observableInterval(0 seconds)
  private var responsesSub: Option[Subscription] = None

  @volatile private var infiniteQueries: Option[Iterator[String]] = None
  @volatile private var started = false

  override def onStart(): Unit = {
    logInfo()

    started = true

    val sub = responsesObs
      .subscribe(
        onNext = onNextQuery,
        onError = { logError(_) }
      )

    responsesSub = Some(sub)
  }

  override def onStop(): Unit = {
    logInfo()
    responsesSub.foreach { sub =>
      logInfo("unsubscribe")
      started = false
      sub.unsubscribe()
      responsesSub = None
      infiniteQueries = None
      rxShutdown()
    }
  }

  private def initializeQueries(): Unit = {
    val iterator = Iterator
      .continually(queries)
      .flatten
      .map(_.toString)
    infiniteQueries = Some(iterator)
  }

  private def onNextQuery(step: Long): Unit = {
    if (step == 0 && infiniteQueries.isEmpty) {
      initializeQueries()
    }

    infiniteQueries match {
      case Some(q) =>
        val query = q.next()
        makeQuery(query, None)
      case None =>
    }
  }

  @tailrec
  private def makeQuery(query: String, page: Option[String]): Unit = {
    logInfo(query)

    def getNextPage(pageInfo: JsLookupResult): Option[String] =
      (pageInfo \ "hasNextPage", pageInfo \ "endCursor") match {
        case (JsDefined(hasNextPage), JsDefined(endCursor))
            if hasNextPage.toString.toBoolean =>
          val nextPage = Some(endCursor.toString)
          logDebug(s"next page is $nextPage")
          nextPage
        case _ =>
          logDebug("no more pages")
          None
      }

    val nextPage = executeGQLBlocking(query, page).flatMap { response =>
      val searchResult: JsLookupResult = response \ "data" \ "search"
      val errors: JsLookupResult = response \ "errors" // TODO: from response or searchResult? test it
      val pageInfo: JsLookupResult = searchResult \ "pageInfo"
      processResponse(searchResult, errors)
      getNextPage(pageInfo)
    }

    if (!nextPage.isEmpty && started) {
      makeQuery(query, nextPage)
    }
  }

  private def processResponse(searchResult: JsLookupResult,
                              errors: JsLookupResult): Unit = {
    logDebug(s"searchResult = `$searchResult`")

    (searchResult, errors) match {
      case (JsDefined(searchResult: JsValue), _) =>
        store(searchResult.toString)
      case (_, JsDefined(errors: JsValue)) => logError(errors)
      case (JsUndefined(), JsUndefined())  => throw new IllegalStateException
    }
  }

  private def executeGQLBlocking(query: String,
                                 page: Option[String]): Option[JsValue] = {
    import hiregooddevs.utils.StringUtils._

    val args = Map(
      "search_query" -> query,
      "page" -> page
        .map(p => "after: " + p)
        .getOrElse(""),
      "type" -> "REPOSITORY",
      "max_results" -> "20",
      "max_commits" -> "2",
      "max_repositories" -> "20",
      "max_pinned_repositories" -> "6",
      "max_topics" -> "20",
      "max_languages" -> "20"
    )

    val gqlQuery: String = queryTemplate.formatTemplate(args)
    val jsonQuery: JsValue = Json.obj("query" -> gqlQuery)
    val result = apiCallBlocking(jsonQuery)

    result match {
      case Success(_) =>
        logDebug("SUCCEED")
      case Failure(error) =>
        logError(s"error ${error.toString}")
    }

    result.toOption
  }

  private def apiCallBlocking(data: JsValue): Try[JsValue] = Try {
    logDebug(data)

    val headers = Map(
      "Authorization" -> s"bearer $apiToken",
      "Content-Type" -> "application/json"
    )

    val response = httpPostBlocking(url = apiURL,
                                    data = data.toString,
                                    headers = headers,
                                    timeout = requestTimeout)
    Json.parse(response)
  }

  // FIXME: replace or make common utils

  private def inputStreamToString(stream: InputStream): String =
    Source
      .fromInputStream(stream)
      .mkString

  private def resourceToString(resourceFilePath: String): String = {
    val stream = getClass.getResourceAsStream(resourceFilePath)
    inputStreamToString(stream)
  }

}
