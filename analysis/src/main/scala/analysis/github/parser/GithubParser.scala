package gitrate.analysis.github.parser

import gitrate.analysis.github.GithubConf
import gitrate.utils.HttpClientFactory.DefaultTimeout
import gitrate.utils.LogUtils

import java.net.URL

import org.apache.commons.codec.binary.Base64.decodeBase64
import org.apache.spark.rdd.RDD
import org.apache.spark.sql._
import org.apache.spark.sql.functions._

import play.api.libs.json.{JsArray, JsDefined, JsValue, JsNumber}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.util.{Try, Success}

class GithubParser(conf: GithubConf) extends Serializable with LogUtils {

  def parseAndFilterJSONs(rdd: RDD[String]): Seq[GithubUser] = {
    val sparkSession = SparkSession.builder.config(rdd.sparkContext.getConf).getOrCreate()
    import sparkSession.implicits._

    def ownerRepos(nodes: Dataset[Row], reposType: String): Dataset[GithubSearchResult] =
      nodes
        .select(
          parseUserId($"nodes.owner.id") as "parsedUserId",
          $"nodes.owner.id" as "ownerIdBase64",
          $"nodes.owner.login" as "ownerLogin",
          explode(col(s"nodes.owner.${reposType}.nodes")) as "repositories"
        )
        .filter( // TODO: refactor
          $"parsedUserId.userType" === "User" && $"repositories.isFork" === false && $"repositories.isMirror" === false &&
            $"repositories.owner.id" === $"ownerIdBase64" &&
            datediff($"repositories.pushedAt", $"repositories.createdAt") >= conf.minRepoAgeDays && isLanguageSupported(
            $"repositories.primaryLanguage.name"))
        .select(
          $"parsedUserId.id" as "ownerId",
          $"ownerLogin",
          $"repositories.id" as "repoIdBase64",
          $"repositories.name" as "repoName",
          $"repositories.primaryLanguage.name" as "repoPrimaryLanguage",
          $"repositories.languages.nodes.name" as "repoLanguages"
        )
        .as[GithubSearchResult]

    val emptySeq = Seq.empty
    if (rdd.isEmpty) emptySeq
    else
      Try {
        val nodes = sparkSession.read
          .json(rdd)
          .select(explode($"nodes") as "nodes")
          .cache()

        val foundRepos = nodes
          .select(
            parseUserId($"nodes.owner.id") as "parsedUserId",
            $"nodes.owner.login" as "ownerLogin",
            $"nodes.id" as "repoIdBase64",
            $"nodes.name" as "repoName",
            $"nodes.createdAt" as "repoCreatedAt",
            $"nodes.pushedAt" as "repoPushedAt",
            $"nodes.primaryLanguage.name" as "repoPrimaryLanguage",
            $"nodes.languages.nodes.name" as "repoLanguages"
          )
          .filter(
            $"parsedUserId.userType" === "User" && datediff($"repoPushedAt", $"repoCreatedAt") >= conf.minRepoAgeDays &&
              isLanguageSupported($"repoPrimaryLanguage"))
          .select($"parsedUserId.id" as "ownerId",
                  $"ownerLogin",
                  $"repoIdBase64",
                  $"repoName",
                  $"repoPrimaryLanguage",
                  $"repoLanguages")
          .as[GithubSearchResult]

        val pinnedRepos = ownerRepos(nodes, "pinnedRepositories")
        val repos = ownerRepos(nodes, "repositories")

        val results: Seq[GithubSearchResult] = foundRepos
          .union(pinnedRepos)
          .union(repos)
          .collect()

        val resultsByUser = results.groupBy((result: GithubSearchResult) => (result.ownerId, result.ownerLogin))
        val partialUsers: Iterable[PartialGithubUser] = for {
          ((id, login), results) <- resultsByUser
          partialRepos = results.map(_.toPartialGithubRepo).distinct
        } yield PartialGithubUser(id, login, partialRepos)

        val futureUsers: Iterable[Future[Try[GithubUser]]] = partialUsers
          .filter((user: PartialGithubUser) => user.partialRepos.length >= conf.minTargetRepos)
          .map((user: PartialGithubUser) => user.requestDetailsAndFilterRepos(this))

        val users: Iterable[GithubUser] = GithubParser.filterSucceedFutures(futureUsers)

        users
          .filter((user: GithubUser) => user.repos.length >= conf.minTargetRepos) // TODO: use details
          .toSeq
      }.logErrors().getOrElse(emptySeq)
  }

  def hasMostlyCommitsByOwnerBlocking(login: String, repoName: String): Try[Boolean] =
    Try {
      val future = hasMostlyCommitsByOwner(login, repoName)
      Await.result(future, DefaultTimeout)
    }.logErrors()

  private[this] def hasMostlyCommitsByOwner(login: String, repoName: String): Future[Boolean] =
    Future {
      val response = apiV3Blocking(s"repos/${login}/${repoName}/stats/participation")
      (response \ "all", response \ "owner") match {
        case (JsDefined(JsArray(all)), JsDefined(JsArray(owner))) =>
          val ratio = arraySum(owner) / arraySum(all)
          ratio >= conf.minOwnerToAllCommitsRatio
        case _ => false
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
      case UserIdRegex(prefix, userType, id) => ParsedUserId(userType, id.toInt)
      case _                                 => ParsedUserId("Unknown", -1)
  })

  private[this] val UserIdRegex = """^(\d*?):(.*?)(\d*)$""".r

  private def arraySum(numbers: Seq[JsValue]): Double =
    numbers
      .map {
        case JsNumber(x: BigDecimal) => x.toLong
        case _                       => 0L
      }
      .sum
      .toDouble

}

object GithubParser {

  // TODO: move to utils?
  def filterSucceedFutures[T](xs: Iterable[Future[Try[T]]]): Iterable[T] = {
    val future = Future
      .sequence(xs)
      .map(_.collect { case Success(x) => x })
    Await.result(future, DefaultTimeout)
  }

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

  def toPartialGithubRepo =
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

      val repos: Seq[GithubRepo] = GithubParser
        .filterSucceedFutures(partialRepos.map(_.requestDetails(githubParser)))
        .filter(_.hasMostlyCommitsByOwner)
        .toSeq

      val userDetailsResult: Option[JsValue] = Try {
        Await.result(userDetails, DefaultTimeout)
      }.toOption

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

      GithubUser(id, login, repos, fullName, description, location, company, email, blog, jobSeeker)
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
      val hasMostlyCommitsByOwner =
        githubParser.hasMostlyCommitsByOwnerBlocking(login = ownerLogin, repoName = name).get
      GithubRepo(idBase64, name, primaryLanguage, languages, hasMostlyCommitsByOwner)
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
                      val hasMostlyCommitsByOwner: Boolean)
