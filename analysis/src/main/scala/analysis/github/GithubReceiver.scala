package gitrate.analysis.github

import gitrate.utils.{LogUtils, ResourceUtils}

import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.receiver.Receiver

import play.api.libs.json.{Json, JsValue, JsLookupResult, JsDefined, JsUndefined, JsBoolean, JsString}

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

class GithubReceiver(conf: GithubConf,
                     loadQueriesFn: () => Seq[GithubSearchQuery],
                     storeResultFn: (GithubReceiver, String) => Unit,
                     storageLevel: StorageLevel = StorageLevel.MEMORY_AND_DISK)
    extends Receiver[String](storageLevel: StorageLevel)
    with LogUtils
    with ResourceUtils {

  private val apiURL = new URL(s"${GithubApiURL}/graphql")
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
    Future(helper).logErrors()

    @tailrec
    def helper(): Unit = {
      if (started.get()) {
        logInfo("reloading queries")
        val queries = loadQueriesFn()

        if (queries.isEmpty) {
          logError("list of queries is empty!")
          val pauseMillis = 10000L
          Thread.sleep(pauseMillis)
        }

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
          storeResultFn(this, result)
        } else {
          logError("receiver is not started")
        }
      case (_, JsDefined(errors: JsValue)) => logError(errors)
      case (JsUndefined(), JsUndefined())  => throw new IllegalStateException
    }
  }

  private def executeGQLBlocking(query: String, page: Option[String]): Option[JsValue] = {
    import gitrate.utils.StringUtils._

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
