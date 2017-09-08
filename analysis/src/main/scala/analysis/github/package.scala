package gitrate.analysis

import gitrate.utils.HttpClientFactory.DefaultTimeout

import java.util.Date
import org.apache.commons.codec.binary.Base64.decodeBase64
import org.apache.spark.sql.catalyst.util.DateTimeUtils.stringToTime
import play.api.libs.json.{JsArray, JsDefined, JsValue, JsNumber}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.util.Try

package object github {

  case class GithubRepo(val idBase64: String,
                        val name: String,
                        createdRaw: String,
                        updatedRaw: String,
                        val primaryLanguage: String,
                        val languages: Seq[String],
                        val isFork: Boolean,
                        val isMirror: Boolean,
                        login: String,
                        reposParser: GithubReposParser) {

    def isTarget: Boolean = isPotentiallyTarget && hasMostlyCommitsByOwnerBlocking

    val created = stringToTime(createdRaw)

    val updated = stringToTime(updatedRaw)

    private val isPotentiallyTarget = {
      val targetLanguage = (reposParser.supportedLanguages contains primaryLanguage)
      val targetAge = daysBetween(created, updated) >= reposParser.minRepoAgeDays
      !isFork && !isMirror && targetLanguage && targetAge
    }

    private[this] def hasMostlyCommitsByOwnerBlocking: Boolean =
      Try(Await.result(hasMostlyCommitsByOwner, DefaultTimeout)).getOrElse(false)

    private[this] val hasMostlyCommitsByOwner: Future[Boolean] = Future {
      if (isPotentiallyTarget) {
        val response = reposParser.apiV3Blocking(s"repos/${login}/${name}/stats/participation")
        (response \ "all", response \ "owner") match {
          case (JsDefined(JsArray(all)), JsDefined(JsArray(owner))) =>
            val ratio = arraySum(owner) / arraySum(all)
            ratio >= reposParser.minOwnerToAllCommitsRatio
          case _ => false
        }
      } else {
        false
      }
    } // TODO: logErrors

  }

  case class GithubUser(val id: Int, val login: String, val repos: Seq[GithubRepo], reposParser: GithubReposParser) {

    def fullName: Option[String] = detailString("name")
    def description: Option[String] = detailString("bio")
    def location: Option[String] = detailString("location")
    def company: Option[String] = detailString("company")

    // FIXME: "The publicly visible email address only displays for authenticated API users"
    def email: Option[String] = detailString("email")
    def blog: Option[String] = detailString("blog")

    def jobSeeker: Option[Boolean] = detailBoolean("hireable")

    private[this] def detailString(field: String): Option[String] =
      detailsBlocking.flatMap(d => (d \ field).asOpt[String])

    private[this] def detailBoolean(field: String): Option[Boolean] =
      detailsBlocking.flatMap(d => (d \ field).asOpt[Boolean])

    private[this] val details: Future[JsValue] = Future {
      val response = reposParser.apiV3Blocking(s"user/$id")
      (response \ "id") match {
        case JsDefined(JsNumber(userId)) if userId.toInt == id =>
          response
        case _ => throw new IllegalStateException(s"Sanity check failed, response was $response")
      }
    }
    // TODO: logErrors

    private[this] def detailsBlocking: Option[JsValue] =
      Try(Await.result(details, DefaultTimeout)).toOption

  }

  def parseUserId(idBase64: String): Option[Int] = {
    base64ToString(idBase64) match {
      case userIdRegex(prefix, userType, id) if userType == "User" => Some(id.toInt)
      case _                                                       => None
    }
  }

  private def daysBetween(from: Date, to: Date): Int = {
    import scala.concurrent.duration._

    val dt = (to.getTime() - from.getTime()).millis
    dt.toDays.toInt.abs
  }

  private def arraySum(numbers: Seq[JsValue]): Double =
    numbers
      .map {
        case JsNumber(x: BigDecimal) => x.toLong
        case _                       => 0L
      }
      .sum
      .toDouble

  def base64ToString(data: String): String =
    new String(decodeBase64(data))

  private[this] val userIdRegex = """^(\d*?):(.*?)(\d*)$""".r

}
