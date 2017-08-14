package hiregooddevs.analysis.github

import hiregooddevs.utils.LogUtils

import java.io.{File, InputStream}
import java.net.{HttpURLConnection, URL}

import org.apache.log4j.Level
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.receiver.Receiver

import play.api.libs.json._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source
import scala.util.{Failure, Success, Try}

class GithubReceiver(apiToken: String,
                     storageLevel: StorageLevel = StorageLevel.MEMORY_AND_DISK)
    extends Receiver[String](storageLevel: StorageLevel)
    with LogUtils {

  log.setLevel(Level.DEBUG) // TODO: move to config

  val requestTimeout = 10 seconds
  val apiURL = "https://api.github.com/graphql"
  val queryTemplate: String = resourceToString("/GithubSearch.graphql")

  @volatile private var started = true

  override def onStart(): Unit = {
    logInfo()
    Future {
      // TODO: initialize from database
      val infiniteQueries: Iterator[String] = Iterator
        .continually(
          List(
            GithubSearchQuery(language = "JavaScript",
                              filename = ".eslintrc.*",
                              maxRepoSizeKiB = 2048),
            GithubSearchQuery(language = "JavaScript",
                              filename = ".travis.yml",
                              maxRepoSizeKiB = 2048)
          ))
        .flatten
        .map(_.toString)

      // TODO: read page from database
      val responses = for {
        query <- infiniteQueries
        if started
        (response, nextPage) <- makeQuery(query)
      } yield (response, query, nextPage)

      responses.foreach {
        case (response, query, nextPage) =>
          processResponse(response, query, nextPage)
      }
    }

    ()
  }

  override def onStop(): Unit = {
    logInfo()
    started = false
  }

  private def makeQuery(
      query: String): Iterator[(JsLookupResult, Option[String])] = {
    logInfo(query)

    val paginator = new Paginator
    val infiniteLoop = Iterator.continually(List(()))

    for {
      _ <- infiniteLoop
      if started && paginator.hasNextPage()
      response <- executeGQLBlocking(query, paginator.nextPage())
      searchResult = response \ "data" \ "search"
      pageInfo = searchResult \ "pageInfo"
      _ = paginator.update(pageInfo)
    } yield (searchResult, paginator.nextPage())
  }

  private def processResponse(response: JsLookupResult,
                              query: String,
                              page: Option[String]): Unit = {
    logDebug(s"query = `$query`, page = $page, response = `$response`")

    val errorMessages: JsLookupResult = response \ "errors"
    errorMessages match {
      case JsDefined(value) => logError(value)
      case _                =>
        // TODO: store object, save page and query
        val result = Iterator(response.toString)
        store(result)
    }
  }

  private def executeGQLBlocking(query: String,
                                 page: Option[String]): Option[JsValue] = {
    val result = Try {
      val responseFuture = executeGQL(query, page)
      Await.result(responseFuture, requestTimeout)
    }

    result match {
      case Success(_) =>
        logDebug("SUCCEED")
      case Failure(error) =>
        logError(s"error ${error.toString}")
    }

    result.toOption
  }

  private def executeGQL(query: String,
                         page: Option[String]): Future[JsValue] = {
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
    apiCall(jsonQuery)
  }

  private def apiCall(data: JsValue): Future[JsValue] = Future {
    logDebug(data)

    val connection =
      new URL(apiURL)
        .openConnection()
        .asInstanceOf[HttpURLConnection] // FIXME: replace with wrapper?
    connection.setConnectTimeout(requestTimeout.toMillis.toInt)
    connection.setRequestMethod("POST")
    connection.setRequestProperty("Authorization", s"bearer $apiToken")
    connection.setRequestProperty("Content-Type", "application/json")

    connection.setDoOutput(true)
    val outputStream = connection.getOutputStream
    outputStream.write(data.toString.getBytes("UTF-8"))
    outputStream.close()

    connection.connect()
    val result = Json.parse(connection.getInputStream)
    result
    // FIXME: interruption handling
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

  private class Paginator {

    def nextPage() = _nextPage

    def hasNextPage() = _hasNextPage

    def update(pageInfo: JsLookupResult): Unit = {
      (pageInfo \ "hasNextPage", pageInfo \ "endCursor") match {
        case (JsDefined(hasNextPage), JsDefined(endCursor))
            if hasNextPage.toString.toBoolean =>
          _nextPage = Some(endCursor.toString)
          logDebug(s"next page is ${nextPage}")
        case _ =>
          logDebug("no more pages")
          _hasNextPage = false
      }
    }

    private var _nextPage: Option[String] = None
    private var _hasNextPage: Boolean = true

  }

}
