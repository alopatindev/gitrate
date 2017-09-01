package hiregooddevs.analysis.github

import hiregooddevs.utils.LogUtils

import java.io.{File, InputStream}

import rx.lang.scala.Subscription

import org.apache.log4j.Level
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.receiver.Receiver

import play.api.libs.json._

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.io.Source
import scala.util.{Failure, Success, Try}

abstract class GithubReceiver(apiToken: String,
                              queries: Seq[GithubSearchQuery],
                              storageLevel: StorageLevel =
                                StorageLevel.MEMORY_AND_DISK)
    extends Receiver[String](storageLevel: StorageLevel)
    with LogUtils {

  log.setLevel(Level.DEBUG) // TODO: move to config

  def httpPostBlocking(url: String,
                       data: String,
                       headers: Map[String, String],
                       timeout: Duration): InputStream

  private val requestTimeout = 10 seconds
  private val apiURL = "https://api.github.com/graphql"

  @transient private lazy val queryTemplate: String =
    resourceToString("/GithubSearch.graphql")

  @volatile private var started = false

  override def onStart(): Unit = {
    logInfo()

    started = true
    run()
  }

  override def onStop(): Unit = {
    logInfo()
    started = false
  }

  private def run(): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global

    Future {
      val infiniteQueries = Iterator
        .continually(queries)
        .flatten
        .map(_.toString)

      while (started) {
        val query = infiniteQueries.next()
        makeQuery(query, None)
      }
    }.failed.foreach(throwable => logError(throwable))

    ()
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

    // TODO: load from config
    val args = Map(
      "search_query" -> query,
      "page" -> page
        .map(p => "after: " + p)
        .getOrElse(""),
      "type" -> "REPOSITORY",
      "max_results" -> "20",
      "max_repositories" -> "20",
      "max_pinned_repositories" -> "6",
      "max_languages" -> "20"
    )

    val gqlQuery: String = queryTemplate.formatTemplate(args)
    val jsonQuery: JsValue = Json.obj("query" -> gqlQuery)
    val result: Try[JsValue] = executeApiCallBlocking(jsonQuery)
    result.failed.foreach(throwable => logError(throwable)) // TODO: extend Future?
    result.toOption
  }

  private def executeApiCallBlocking(data: JsValue): Try[JsValue] = Try {
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
