package gitrate.analysis.github.parser

import gitrate.analysis.github.GithubConf
import gitrate.utils.HttpClientFactory.DefaultTimeout
import gitrate.utils.ConcurrencyUtils
import gitrate.utils.LogUtils

import java.net.URL

import org.apache.commons.codec.binary.Base64.decodeBase64
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Dataset, Row, SparkSession}

import play.api.libs.json.{JsArray, JsDefined, JsValue, JsNumber}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.util.Try

class GithubParser(val conf: GithubConf) extends Serializable with LogUtils {

  import org.apache.spark.sql.functions._

  def parseAndFilterUsers(rawJSONs: RDD[String]): Iterable[GithubUser] = {
    val emptySeq = Iterable()
    if (rawJSONs.isEmpty) {
      emptySeq
    } else {
      Try {
        val conf = rawJSONs.sparkContext.getConf
        implicit val sparkSession = SparkSession.builder // TODO: move to SparkUtils
          .config(conf)
          .getOrCreate()
        processUsers(rawJSONs)
      }.logErrors().getOrElse(emptySeq)
    }
  }

  private def processUsers(rawJSONs: RDD[String])(implicit sparkSession: SparkSession): Iterable[GithubUser] = {
    import sparkSession.implicits._

    val rawNodes = sparkSession.read
      .json(rawJSONs)
      .select(explode($"nodes") as "nodes")
      .cache()

    val foundRepos: Dataset[GithubSearchResult] = processFoundRepos(rawNodes)
    val pinnedRepos: Dataset[GithubSearchResult] = processOwnerRepos(rawNodes, "pinnedRepositories")
    val repos: Dataset[GithubSearchResult] = processOwnerRepos(rawNodes, "repositories")

    val results: Seq[GithubSearchResult] = foundRepos
      .union(pinnedRepos)
      .union(repos)
      .collect()

    val resultsByUser = results.groupBy((result: GithubSearchResult) => (result.ownerId, result.ownerLogin))
    val partialUsers: Iterable[PartialGithubUser] = for {
      ((id, login), results) <- resultsByUser
      partialRepos = results
        .map(_.toPartialGithubRepo)
        .distinct
    } yield PartialGithubUser(id, login, partialRepos)

    val futureUsers: Iterable[Future[Try[GithubUser]]] = partialUsers
      .filter((user: PartialGithubUser) => user.partialRepos.length >= conf.minTargetRepos)
      .map((user: PartialGithubUser) => user.requestDetailsAndFilterRepos(this))

    val users: Iterable[GithubUser] = ConcurrencyUtils.filterSucceedFutures(futureUsers, timeout = DefaultTimeout)

    users.filter((user: GithubUser) => user.repos.length >= conf.minTargetRepos) // TODO: use details
  }

  private def filterRepos(rawNodes: Dataset[Row], prefix: String)(implicit sparkSession: SparkSession): Dataset[Row] = {
    import sparkSession.implicits._

    rawNodes.filter($"parsedUserId.userType" === "User" &&
      datediff(col(s"${prefix}.pushedAt"), col(s"${prefix}.createdAt")) >= conf.minRepoAgeDays && isLanguageSupported(
      col(s"${prefix}.primaryLanguage.name")))
  }

  private def processFoundRepos(rawNodes: Dataset[Row])(
      implicit sparkSession: SparkSession): Dataset[GithubSearchResult] = {
    import sparkSession.implicits._

    val rawFoundRepos = rawNodes
      .select(
        parseUserId($"nodes.owner.id") as "parsedUserId",
        $"nodes.owner.login" as "ownerLogin",
        $"nodes.id" as "repoIdBase64",
        $"nodes.name" as "repoName",
        $"nodes.primaryLanguage.name" as "repoPrimaryLanguage",
        $"nodes.languages.nodes.name" as "repoLanguages"
      )

    filterRepos(rawFoundRepos, "nodes")
      .select($"parsedUserId.id" as "ownerId",
              $"ownerLogin",
              $"repoIdBase64",
              $"repoName",
              $"repoPrimaryLanguage",
              $"repoLanguages")
      .as[GithubSearchResult]
  }

  private def processOwnerRepos(rawNodes: Dataset[Row], reposType: String)(
      implicit sparkSession: SparkSession): Dataset[GithubSearchResult] = {
    import sparkSession.implicits._

    val rawRepos = rawNodes.select(
      parseUserId($"nodes.owner.id") as "parsedUserId",
      $"nodes.owner.id" as "ownerIdBase64",
      $"nodes.owner.login" as "ownerLogin",
      explode(col(s"nodes.owner.${reposType}.nodes")) as "repositories"
    )

    val repos = filterRepos(rawRepos, "repositories")
    repos
      .filter(
        $"repositories.isFork" === false && $"repositories.isMirror" === false &&
          $"repositories.owner.id" === $"ownerIdBase64")
      .select(
        $"parsedUserId.id" as "ownerId",
        $"ownerLogin",
        $"repositories.id" as "repoIdBase64",
        $"repositories.name" as "repoName",
        $"repositories.primaryLanguage.name" as "repoPrimaryLanguage",
        $"repositories.languages.nodes.name" as "repoLanguages"
      )
      .as[GithubSearchResult]
  }

  def ownerToAllCommitsRatioBlocking(login: String, repoName: String): Try[Double] = // TODO: move to PartialGithubRepo?
    Try {
      val future = ownerToAllCommitsRatio(login, repoName)
      Await.result(future, DefaultTimeout)
    }.logErrors()

  private[this] def ownerToAllCommitsRatio(login: String, repoName: String): Future[Double] =
    Future {
      val response = apiV3Blocking(s"repos/${login}/${repoName}/stats/participation")
      (response \ "all", response \ "owner") match {
        case (JsDefined(JsArray(all)), JsDefined(JsArray(owner))) => arraySum(owner) / arraySum(all)
        case _                                                    => 0.0
      }
    }.logErrors()

  def apiV3Blocking(path: String): JsValue = {
    val url = new URL(s"https://api.github.com/${path}")
    val headers = Map(
      "Authorization" -> s"token ${conf.apiToken}",
      "Accept" -> "application/vnd.github.v3.json"
    )
    conf.httpGetBlocking(url, headers)
  }

  private val isLanguageSupported = udf((language: String) => conf.supportedLanguages contains language)

  private val parseUserId = udf((idBase64: String) =>
    new String(decodeBase64(idBase64)) match {
      case userIdRegex(prefix, userType, id) => ParsedUserId(userType, id.toInt)
      case _                                 => ParsedUserId("Unknown", -1)
  })

  private[this] val userIdRegex = """^(\d*?):(.*?)(\d*)$""".r

  private def arraySum(numbers: Seq[JsValue]): Double =
    numbers
      .map {
        case JsNumber(x: BigDecimal) => x.toLong
        case _                       => 0L
      }
      .sum
      .toDouble

}

case class ParsedUserId(userType: String, id: Int)

case class GithubSearchResult(
    val ownerId: Int,
    val ownerLogin: String,
    val repoIdBase64: String,
    val repoName: String,
    val repoPrimaryLanguage: String,
    val repoLanguages: Seq[String]
) {

  def toPartialGithubRepo: PartialGithubRepo =
    PartialGithubRepo(repoIdBase64, repoName, repoPrimaryLanguage, repoLanguages, ownerLogin) // TODO: remove login?

}

case class PartialGithubUser(val id: Int, val login: String, val partialRepos: Seq[PartialGithubRepo])
    extends LogUtils {

  def requestDetailsAndFilterRepos(githubParser: GithubParser): Future[Try[GithubUser]] = Future {
    Try {
      val userDetails: Future[JsValue] = Future {
        val response = githubParser.apiV3Blocking(s"user/${id}")
        (response \ "id") match {
          case JsDefined(JsNumber(userId)) if userId.toInt == id =>
            response
          case _ => throw new IllegalStateException(s"Sanity check failed, response was $response")
        }
      }.logErrors()

      val repos: Seq[Future[Try[GithubRepo]]] = partialRepos.map(_.requestDetails(githubParser))
      val filteredRepos: Seq[GithubRepo] = ConcurrencyUtils
        .filterSucceedFutures(repos, timeout = DefaultTimeout)
        .filter(repo => repo.ownerToAllCommitsRatio >= githubParser.conf.minOwnerToAllCommitsRatio)
        .toSeq

      val userDetailsResult: Option[JsValue] = Try(Await.result(userDetails, DefaultTimeout)).toOption

      def userDetailString(field: String): Option[String] =
        userDetailsResult.flatMap(d => (d \ field).asOpt[String])

      def userDetailBoolean(field: String): Option[Boolean] =
        userDetailsResult.flatMap(d => (d \ field).asOpt[Boolean])

      val fullName: Option[String] = userDetailString("name")
      val description: Option[String] = userDetailString("bio")
      val location: Option[String] = userDetailString("location")
      val company: Option[String] = userDetailString("company")
      val email: Option[String] = userDetailString("email")
      val blog: Option[String] = userDetailString("blog")
      val jobSeeker: Option[Boolean] = userDetailBoolean("hireable")

      GithubUser(id, login, filteredRepos, fullName, description, location, company, email, blog, jobSeeker)
    }.logErrors()
  }

}

case class PartialGithubRepo(val idBase64: String,
                             val name: String,
                             val primaryLanguage: String,
                             val languages: Seq[String],
                             ownerLogin: String) {

  def requestDetails(githubParser: GithubParser): Future[Try[GithubRepo]] = Future {
    Try {
      val ownerToAllCommitsRatio = githubParser.ownerToAllCommitsRatioBlocking(login = ownerLogin, repoName = name)
      GithubRepo(idBase64, name, primaryLanguage, languages, ownerToAllCommitsRatio.get)
    }
  }

}

case class GithubUser(val id: Int,
                      val login: String,
                      val repos: Seq[GithubRepo],
                      val fullName: Option[String],
                      val description: Option[String],
                      val location: Option[String],
                      val company: Option[String],
                      val email: Option[String],
                      val blog: Option[String],
                      val jobSeeker: Option[Boolean])

case class GithubRepo(val idBase64: String,
                      val name: String,
                      val primaryLanguage: String,
                      val languages: Seq[String],
                      val ownerToAllCommitsRatio: Double)
