package hiregooddevs.analysis.github

import hiregooddevs.utils.HttpClientFactory.HttpPostFunction
import hiregooddevs.utils.LogUtils

import java.io.InputStream
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.receiver.Receiver

import play.api.libs.json.{Json, JsValue, JsLookupResult, JsDefined, JsUndefined}

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.io.Source
import scala.util.Try

case class GithubConf(val apiToken: String,
                      val maxResults: Int,
                      val maxRepositories: Int,
                      val maxPinnedRepositories: Int,
                      val maxLanguages: Int)

class GithubReceiver(conf: GithubConf, storageLevel: StorageLevel = StorageLevel.MEMORY_AND_DISK)(
    httpPostBlocking: HttpPostFunction[JsValue, JsValue],
    onLoadQueries: () => Seq[GithubSearchQuery],
    onStoreResult: (GithubReceiver, String) => Unit
) extends Receiver[String](storageLevel: StorageLevel)
    with LogUtils {

  private val apiURL = new URL("https://api.github.com/graphql")
  @transient private lazy val queryTemplate: String = resourceToString("/GithubSearch.graphql")

  private val started = new AtomicBoolean(false) // scalastyle:ignore

  override def onStart(): Unit = {
    logInfo()

    started.set(true)
    run()
  }

  override def onStop(): Unit = {
    logInfo()
    started.set(false)
  }

  private def run(): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global

    Future(helper).logErrors()

    @tailrec
    def helper(): Unit = {
      if (started.get()) {
        logInfo("reloading queries")
        val queries = onLoadQueries()

        queries
          .map(_.toString)
          .foreach(query => makeQuery(query, None))

        helper()
      }
    }
  }

  @tailrec
  private def makeQuery(query: String, page: Option[String]): Unit = {
    logDebug(query)

    def getNextPage(pageInfo: JsLookupResult): Option[String] =
      (pageInfo \ "hasNextPage", pageInfo \ "endCursor") match {
        case (JsDefined(hasNextPage), JsDefined(endCursor)) if hasNextPage.toString.toBoolean =>
          val nextPage = Some(endCursor.toString)
          logDebug(s"next page is $nextPage")
          nextPage
        case _ =>
          logDebug("no more pages")
          None
      }

    val nextPage = executeGQLBlocking(query, page).flatMap { response =>
      val searchResult: JsLookupResult = response \ "data" \ "search"
      val errors: JsLookupResult = response \ "errors"
      val pageInfo: JsLookupResult = searchResult \ "pageInfo"
      processResponse(searchResult, errors)
      getNextPage(pageInfo)
    }

    nextPage match {
      case Some(_) if started.get() => makeQuery(query, nextPage)
      case _                        =>
    }
  }

  private def processResponse(searchResult: JsLookupResult, errors: JsLookupResult): Unit = {
    (searchResult, errors) match {
      case (JsDefined(searchResult: JsValue), _) =>
        val result = searchResult.toString // JSON string is easier to serialize
        onStoreResult(this, result)
      case (_, JsDefined(errors: JsValue)) => logError(errors)
      case (JsUndefined(), JsUndefined())  => throw new IllegalStateException
    }
  }

  private def executeGQLBlocking(query: String, page: Option[String]): Option[JsValue] = {
    import hiregooddevs.utils.StringUtils._

    val args = Map(
      "searchQuery" -> query,
      "page" -> page
        .map(p => "after: " + p)
        .getOrElse(""),
      "type" -> "REPOSITORY",
      "maxResults" -> conf.maxResults.toString,
      "maxRepositories" -> conf.maxRepositories.toString,
      "maxPinnedRepositories" -> conf.maxPinnedRepositories.toString,
      "maxLanguages" -> conf.maxLanguages.toString
    )

    val gqlQuery: String = queryTemplate.formatTemplate(args)
    val jsonQuery: JsValue = Json.obj("query" -> gqlQuery)
    executeApiCallBlocking(jsonQuery)
      .logErrors()
      .toOption
  }

  private def executeApiCallBlocking(data: JsValue): Try[JsValue] = Try {
    logDebug(data)

    val headers = Map(
      "Authorization" -> s"bearer ${conf.apiToken}",
      "Content-Type" -> "application/json"
    )

    httpPostBlocking(apiURL, data, headers)
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
