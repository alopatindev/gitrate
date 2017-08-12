package hiregooddevs.analysis.github

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import java.io.{File, InputStream}
import java.net.URL

import org.apache.log4j.{Level, Logger, LogManager}
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.receiver.Receiver

import play.api.libs.json._
import play.api.libs.ws._
import play.api.libs.ws.ahc._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source
import scala.util.{Success, Try}

class GithubReceiver(apiToken: String,
                     storageLevel: StorageLevel = StorageLevel.MEMORY_AND_DISK)
    extends Receiver[String](storageLevel: StorageLevel) {

  @transient lazy val log = LogManager.getLogger(getClass.getSimpleName)
  log.setLevel(Level.DEBUG) // TODO: move to config

  // TODO: move to separate module
  def cutLongDebugData(data: Any): String = {
    val limit = 10
    data.toString.take(limit)
  }

  // TODO: move to separate module
  // FIXME: get actor system from spark?
  @transient lazy implicit val system = ActorSystem()
  @transient lazy implicit val materializer = ActorMaterializer()
  @transient lazy val ws = StandaloneAhcWSClient()

  val requestTimeout = 10000 millis
  val apiURL = "https://api.github.com/graphql"
  val queryTemplate: String = resourceToString("/GithubSearch.graphql")

  @volatile private var started = true

  override def onStart(): Unit = {
    log.info("onStart")
    Future {
      // TODO: initialize from database
      // FIXME: load from config
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

      val firstPage = None
      val _ = (for {
        query <- infiniteQueries
        if started

        _ = log.info(s"start QUERY `$query`")

        firstResponse <- executeGQLBlocking(query, firstPage)
        _ = processResponse(firstResponse, query, firstPage)

        searchResult = firstResponse \ "data" \ "search"
        pageInfo = searchResult \ "pageInfo"

        JsDefined(hasNextPage) = pageInfo \ "hasNextPage"
        if hasNextPage.toString.toBoolean

        JsDefined(nextPage) = pageInfo \ "endCursor"
        nextPageOption = Some(nextPage.toString)

        _ = log.info(s"start SUBQUERY, page `$nextPageOption`")
        response <- executeGQLBlocking(query, nextPageOption)
        _ = processResponse(response, query, nextPageOption)
      } yield ()).size // force computations
    }

    ()
  }

  override def onStop(): Unit = {
    log.info("onStop")
    started = false
  }

  private def processResponse(response: JsValue,
                              query: String,
                              page: Option[String]): Unit = {
    log.debug(
      s"processResponse(`${cutLongDebugData(response)}`, `$query`, `$page`)")

    val errorMessages: JsLookupResult = response \ "errors"
    errorMessages match {
      case JsDefined(value) => log.error(value.toString)
      case _                => ()
    }

    // TODO: save page and query
    // FIXME: error handling?
    val result = Iterator(response.toString)
    store(result)
  }

  def executeGQLBlocking(query: String,
                         page: Option[String]): Option[JsValue] =
    Try {
      val responseFuture = executeGQL(query, page)
      Await.result(responseFuture, requestTimeout)
    }.toOption

  def executeGQL(query: String, page: Option[String]): Future[JsValue] = {
    import hiregooddevs.utils.StringUtils._

    val args = Map(
      "search_query" -> query,
      "page" -> page
      //.map(p => "after: \"%s\"".format(p))
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

  def apiCall(data: JsValue): Future[JsValue] = {
    import play.api.libs.ws.JsonBodyReadables._
    import play.api.libs.ws.JsonBodyWritables._
    import scala.concurrent.ExecutionContext.Implicits._

    log.debug(s"apiCall data = `${cutLongDebugData(data)}`")

    ws.url(apiURL)
      .addHttpHeaders("Authorization" -> s"bearer $apiToken")
      .withRequestTimeout(requestTimeout)
      .post(data)
      .map(response => response.body[JsValue])
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
