package hiregooddevs.analysis.github

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import java.io.{File, InputStream}
import java.net.URL

import org.apache.commons.lang.text.StrSubstitutor
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

  val requestTimeout = 10000 millis
  val apiURL = "https://api.github.com/graphql"
  val queryTemplate = resourceToString("GithubSearch.graphql")

  // TODO: move to separate module
  // FIXME: get actor system from spark?
  implicit val system = ActorSystem()
  /*system.registerOnTermination {
    System.exit(0)
  }*/
  implicit val materializer = ActorMaterializer()
  val ws = StandaloneAhcWSClient()

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
    .map(_.toString)

  @volatile private var started = true

  override def onStart(): Unit = {
    Future {
      val firstPage = None
      for {
        query <- infiniteQueries
        if started

        _ = println(s"start new query `$query`")

        firstResponse <- gqlBlocking(query, firstPage)
        _ = processResponse(firstResponse, query, firstPage)

        firstResponseParsed = firstResponse
        pageInfo = firstResponseParsed \ "pageInfo"

        JsDefined(hasNextPage) = pageInfo \ "hasNextPage"
        hasNextPageValue = hasNextPage.toString.toBoolean
        if hasNextPageValue

        JsDefined(nextPage) = pageInfo \ "endCursor"
        nextPageOption = Some(nextPage.toString)

        response: JsValue <- gqlBlocking(query, nextPageOption)
        _ = processResponse(response, query, nextPageOption)
      } yield ()
    }

    ()
  }

  override def onStop(): Unit = {
    started = false
  }

  private def processResponse(response: JsValue,
                              query: String,
                              page: Option[String]): Unit = {
    println(s"processResponse($response, $query, $page)")
    // TODO: save page and query
    // FIXME: error handling?
    val result = Iterator(response.toString)
    store(result)
  }

  private def gqlBlocking(query: String,
                          page: Option[String]): Option[JsValue] =
    Try {
      val responseFuture = gql(query, page)
      Await.result(responseFuture, requestTimeout)
    }.toOption

  private def gql(query: String, page: Option[String]): Future[JsValue] = {
    val pageString = page
      .map(p => s"after: '${p}'")
      .getOrElse("")
    val args = Map(
      "search_query" -> query,
      "max_results" -> "20",
      "max_commits" -> "2",
      "max_repositories" -> "20",
      "max_pinned_repositories" -> "6",
      "max_topics" -> "20",
      "max_languages" -> "20",
      "page" -> pageString
    )
    val gqlQuery = formatTemplate(queryTemplate, args)
    val jsonQuery: JsValue = Json.obj("query" -> gqlQuery)
    println(jsonQuery)
    apiCall(jsonQuery)
  }

  private def apiCall(data: JsValue): Future[JsValue] = {
    import play.api.libs.ws.JsonBodyReadables._
    import play.api.libs.ws.JsonBodyWritables._
    import scala.concurrent.ExecutionContext.Implicits._

    ws.url(apiURL)
      .addHttpHeaders("Authorization" -> s"bearer $apiToken")
      .withRequestTimeout(requestTimeout)
      .post(data)
      .map(response => response.body[JsValue])
  }

  // FIXME: replace or make common utils

  private def formatTemplate(template: String,
                             args: Map[String, String]): String = {
    import scala.collection.JavaConverters._
    val sub = new StrSubstitutor(args.asJava)
    sub.replace(template)
  }

  private def inputStreamToString(stream: InputStream): String =
    Source
      .fromInputStream(stream)
      .mkString

  private def resourceToString(resourceFilePath: String): String = {
    val stream = getClass.getResourceAsStream(resourceFilePath)
    inputStreamToString(stream)
  }

}
