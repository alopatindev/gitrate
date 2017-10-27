package analysis.github

import controllers.GithubController.AnalyzedRepository
import utils.HttpClientFactory.defaultTimeout
import utils.SparkUtils.RDDUtils
import utils.{ConcurrencyUtils, LogUtils}

import java.net.URL
import org.apache.commons.codec.binary.Base64.decodeBase64
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.functions._
import org.apache.spark.sql.{Dataset, Row, SparkSession}
import play.api.libs.json.{JsArray, JsDefined, JsNumber, JsValue}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.util.Try

class GithubExtractor(val conf: GithubConf, loadAnalyzedRepositories: (Seq[String]) => Dataset[AnalyzedRepository])
    extends Serializable
    with LogUtils {

  def parseAndFilterUsers(rawJSONs: RDD[String]): Iterable[GithubUser] = {
    val emptySeq = Iterable.empty
    if (rawJSONs.isEmpty) {
      emptySeq
    } else {
      Try {
        implicit val sparkSession: SparkSession = rawJSONs.toSparkSession
        processUsers(rawJSONs)
      }.logErrors().getOrElse(emptySeq)
    }
  }

  private def processUsers(rawJSONs: RDD[String])(implicit sparkSession: SparkSession): Iterable[GithubUser] = {
    import sparkSession.implicits._

    val rawNodes = sparkSession.read
      .json(rawJSONs.toDS)
      .select(explode('nodes) as "nodes")
      .cache()

    val extractedRepositories = processFoundRepositories(rawNodes)
      .union(processOwnerRepositories(rawNodes, "pinnedRepositories"))
      .union(processOwnerRepositories(rawNodes, "repositories"))
      .cache()

    val repoIdsBase64: Seq[String] = extractedRepositories
      .select('repoIdBase64)
      .as[String]
      .collect()

    val analyzedRepositories: Dataset[AnalyzedRepository] = loadAnalyzedRepositories(repoIdsBase64)

    val results: Seq[GithubSearchResult] = extractedRepositories
      .join(analyzedRepositories, 'repoIdBase64 === 'idBase64, joinType = "left")
      .filter('updatedByAnalyzer.isNull ||
        datediff(current_date(), 'updatedByAnalyzer) >= conf.minRepositoryUpdateInterval.toDays)
      .select('ownerId, 'ownerLogin, 'repoIdBase64, 'repoName, 'repoPrimaryLanguage, 'repoLanguages, 'defaultBranch)
      .distinct
      .as[GithubSearchResult]
      .collect()

    val resultsByUser: Map[(Int, String), Seq[GithubSearchResult]] =
      results.groupBy((result: GithubSearchResult) => (result.ownerId, result.ownerLogin))
    val partialUsers: Iterable[PartialGithubUser] = for {
      ((id, login), results) <- resultsByUser
      partialRepos = results.map(_.toPartialGithubRepository)
    } yield PartialGithubUser(id, login, partialRepos)

    val futureUsers: Iterable[Future[Try[GithubUser]]] = partialUsers
      .filter((user: PartialGithubUser) => user.partialRepositories.length >= conf.minTargetRepositories)
      .map((user: PartialGithubUser) => user.requestDetailsAndFilterRepos(this))

    val users: Iterable[GithubUser] = ConcurrencyUtils.filterSucceedFutures(futureUsers, timeout = defaultTimeout)
    users.filter((user: GithubUser) => user.repositories.length >= conf.minTargetRepositories)
  }

  private def filterRepos(rawNodes: Dataset[Row], prefix: String)(implicit sparkSession: SparkSession): Dataset[Row] = {
    import sparkSession.implicits._

    rawNodes.filter(
      $"parsedUserId.userType" === "User" &&
        datediff(col(s"$prefix.pushedAt"), col(s"$prefix.createdAt")) >= conf.minRepoAge.toDays &&
        isLanguageSupported(col(s"$prefix.primaryLanguage.name")))
  }

  private def processFoundRepositories(rawNodes: Dataset[Row])(
      implicit sparkSession: SparkSession): Dataset[GithubSearchResult] = {
    import sparkSession.implicits._

    val rawFoundRepos = rawNodes
      .select(
        parseUserId($"nodes.owner.id") as "parsedUserId",
        $"nodes.owner.login" as "ownerLogin",
        $"nodes.id" as "repoIdBase64",
        $"nodes.name" as "repoName",
        $"nodes.primaryLanguage.name" as "repoPrimaryLanguage",
        $"nodes.languages.nodes.name" as "repoLanguages",
        $"nodes.defaultBranchRef.name" as "defaultBranch"
      )

    filterRepos(rawFoundRepos, "nodes")
      .select($"parsedUserId.id" as "ownerId",
              'ownerLogin,
              'repoIdBase64,
              'repoName,
              'repoPrimaryLanguage,
              'repoLanguages,
              'defaultBranch)
      .as[GithubSearchResult]
  }

  private def processOwnerRepositories(rawNodes: Dataset[Row], repositoryType: String)(
      implicit sparkSession: SparkSession): Dataset[GithubSearchResult] = {
    import sparkSession.implicits._

    val rawRepositories = rawNodes.select(
      parseUserId($"nodes.owner.id") as "parsedUserId",
      $"nodes.owner.id" as "ownerIdBase64",
      $"nodes.owner.login" as "ownerLogin",
      explode(col(s"nodes.owner.$repositoryType.nodes")) as "repositories"
    )

    val repositories = filterRepos(rawRepositories, "repositories")
    repositories
      .filter(
        $"repositories.isFork" === false && $"repositories.isMirror" === false &&
          $"repositories.owner.id" === 'ownerIdBase64)
      .select(
        $"parsedUserId.id" as "ownerId",
        'ownerLogin,
        $"repositories.id" as "repoIdBase64",
        $"repositories.name" as "repoName",
        $"repositories.primaryLanguage.name" as "repoPrimaryLanguage",
        $"repositories.languages.nodes.name" as "repoLanguages",
        $"repositories.defaultBranchRef.name" as "defaultBranch"
      )
      .as[GithubSearchResult]
  }

  def ownerToAllCommitsRatioBlocking(login: String, repoName: String): Try[Double] =
    Try {
      val future = ownerToAllCommitsRatio(login, repoName)
      Await.result(future, defaultTimeout)
    }.logErrors()

  private[this] def ownerToAllCommitsRatio(login: String, repoName: String): Future[Double] =
    Future {
      val response = apiV3Blocking(s"repos/$login/$repoName/stats/participation")
      (response \ "all", response \ "owner") match {
        case (JsDefined(JsArray(all)), JsDefined(JsArray(owner))) => arraySum(owner) / arraySum(all)
        case _                                                    => 0.0
      }
    }.logErrors()

  def apiV3Blocking(path: String): JsValue = {
    val url = new URL(s"$githubApiURL/$path")
    val headers = Map(
      "Authorization" -> s"token ${conf.apiToken}",
      "Accept" -> "application/vnd.github.v3.json"
    )
    conf.httpGetBlocking(url, headers)
  }

  private[this] val isLanguageSupported = udf((language: String) => conf.supportedLanguages contains language)

  private val parseUserId = udf((idBase64: String) =>
    new String(decodeBase64(idBase64)) match {
      case userIdRegex(_, userType, id) => ParsedUserId(userType, id.toInt)
      case _                            => ParsedUserId("Unknown", -1)
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
    ownerId: Int,
    ownerLogin: String,
    repoIdBase64: String,
    repoName: String,
    repoPrimaryLanguage: String,
    repoLanguages: Seq[String],
    defaultBranch: String
) {

  def toPartialGithubRepository: PartialGithubRepository =
    PartialGithubRepository(repoIdBase64, repoName, repoPrimaryLanguage, repoLanguages, defaultBranch, ownerLogin)

}

case class PartialGithubUser(id: Int, login: String, partialRepositories: Seq[PartialGithubRepository])
    extends LogUtils {

  def requestDetailsAndFilterRepos(githubExtractor: GithubExtractor): Future[Try[GithubUser]] = Future {
    Try {
      val userDetails: Future[JsValue] = Future {
        val response = githubExtractor.apiV3Blocking(s"user/$id")
        response \ "id" match {
          case JsDefined(JsNumber(userId)) if userId.toInt == id =>
            response
          case _ => throw new IllegalStateException(s"Sanity check failed, response was $response")
        }
      }.logErrors()

      val repositories: Seq[Future[Try[GithubRepository]]] = partialRepositories.map(_.requestDetails(githubExtractor))
      val filteredRepositories: Seq[GithubRepository] = ConcurrencyUtils
        .filterSucceedFutures(repositories, timeout = defaultTimeout)
        .filter(repo => repo.ownerToAllCommitsRatio >= githubExtractor.conf.minOwnerToAllCommitsRatio)
        .toSeq

      val userDetailsResult: Option[JsValue] = Try(Await.result(userDetails, defaultTimeout)).toOption

      def userDetailString(field: String): Option[String] =
        userDetailsResult.flatMap(d => (d \ field).asOpt[String].filter(_.nonEmpty))

      def userDetailBoolean(field: String): Option[Boolean] =
        userDetailsResult.flatMap(d => (d \ field).asOpt[Boolean])

      val fullName: Option[String] = userDetailString("name")
      val description: Option[String] = userDetailString("bio")
      val location: Option[String] = userDetailString("location")
      val company: Option[String] = userDetailString("company")
      val email: Option[String] = userDetailString("email")
      val blog: Option[String] = userDetailString("blog")
      val jobSeeker: Option[Boolean] = userDetailBoolean("hireable")

      GithubUser(id, login, filteredRepositories, fullName, description, location, company, email, blog, jobSeeker)
    }.logErrors()
  }

}

case class PartialGithubRepository(idBase64: String,
                                   name: String,
                                   primaryLanguage: String,
                                   languages: Seq[String],
                                   defaultBranch: String,
                                   ownerLogin: String) {

  def requestDetails(githubExtractor: GithubExtractor): Future[Try[GithubRepository]] = Future {
    Try {
      val ownerToAllCommitsRatio = githubExtractor.ownerToAllCommitsRatioBlocking(login = ownerLogin, repoName = name)
      val archiveURL = new URL(s"$githubURL/$ownerLogin/$name/archive/$defaultBranch.tar.gz")
      GithubRepository(idBase64,
                       name,
                       primaryLanguage,
                       languages,
                       defaultBranch,
                       archiveURL,
                       ownerToAllCommitsRatio.get)
    }
  }

}

case class GithubUser(id: Int,
                      login: String,
                      repositories: Seq[GithubRepository],
                      fullName: Option[String],
                      description: Option[String],
                      location: Option[String],
                      company: Option[String],
                      email: Option[String],
                      blog: Option[String],
                      jobSeeker: Option[Boolean])

case class GithubRepository(idBase64: String,
                            name: String,
                            primaryLanguage: String,
                            languages: Seq[String],
                            defaultBranch: String,
                            archiveURL: URL,
                            ownerToAllCommitsRatio: Double)
