package analysis.github

import controllers.GithubController.GithubSearchQuery
import utils.{LogUtils, ResourceUtils}

import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.receiver.Receiver
import play.api.libs.json.{JsBoolean, JsDefined, JsLookupResult, JsString, JsUndefined, JsValue, Json}
import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

class GithubReceiver(conf: GithubConf,
                     loadQueries: () => (Seq[GithubSearchQuery], Int),
                     saveReceiverState: (Int) => Unit,
                     storeResult: (GithubReceiver, String) => Unit,
                     storageLevel: StorageLevel = StorageLevel.MEMORY_AND_DISK)
    extends Receiver[String](storageLevel: StorageLevel)
    with LogUtils
    with ResourceUtils {

  private val apiURL = new URL(s"$githubApiURL/graphql")
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
    Future(helper()).logErrors()

    @tailrec
    def helper(): Unit = {
      if (started.get()) {
        logInfo("reloading queries")
        val (queries, loadedQueryIndex) = loadQueries()

        logInfo(s"${queries.length} queries, previous index is $loadedQueryIndex")

        if (queries.isEmpty) {
          logError("list of queries is empty!")
          val pauseMillis = 10000L
          Thread.sleep(pauseMillis)
        }

        val queriesToProcess = queries.map(_.toString)
        val totalQueries = queriesToProcess.length

        queriesToProcess.zipWithIndex
          .drop((loadedQueryIndex + 1) % totalQueries)
          .foreach {
            case (query, queryIndex) => {
              if (started.get()) {
                logInfo(s"running new query (${queryIndex + 1}/$totalQueries) $query")
                saveReceiverState(queryIndex)
                makeQuery(query, None)
              }
            }
          }

        helper()
      }
    }
  }

  @tailrec
  private def makeQuery(query: String, page: Option[String]): Unit = {
    logDebug(query)

    def getNextPage(pageInfo: JsLookupResult): Option[String] =
      (pageInfo \ "hasNextPage", pageInfo \ "endCursor") match {
        case (JsDefined(JsBoolean(hasNextPage)), JsDefined(JsString(endCursor))) if hasNextPage =>
          val nextPage = Some(endCursor)
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
        if (started.get()) {
          storeResult(this, result)
        } else {
          logError("receiver is not started")
        }
      case (_, JsDefined(errors: JsValue)) => logError(errors)
      case (JsUndefined(), JsUndefined())  => throw new IllegalStateException
    }
  }

  private def executeGQLBlocking(query: String, page: Option[String]): Option[JsValue] = {
    import utils.StringUtils._

    val args = Map(
      "searchQuery" -> query,
      "page" -> page
        .map(p => s"""after: "$p"""")
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

    conf.httpPostBlocking(apiURL, data, headers)
  }

}
