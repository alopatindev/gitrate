package gitrate.analysis

package object github {

  import java.util.Date

  import org.apache.commons.codec.binary.Base64.decodeBase64

  case class GithubRepo(val idBase64: String,
                        val name: String,
                        val created: Date,
                        val updated: Date,
                        val primaryLanguage: String,
                        val languages: Seq[String],
                        val isFork: Boolean,
                        val isMirror: Boolean) {
    /*val isOld: Boolean = ???
    val hasMostlyCommitsByOwner: Boolean = ???
    val userIsOwner: Boolean = ???
    val isLanguageSupported: Boolean = ???*/
  }

  case class GithubUser(val id: String, val login: String, val repos: Seq[GithubRepo]) {
    lazy val email: Option[String] = ???
    /*def repo(name: String): GithubRepo = ???
    def targetRepos: Seq[GithubRepo] = ???
    val isValid: Boolean = ???
    val isTarget: Boolean = ???*/
  }

  def parseAndFilterUserId(idBase64: String): Option[String] = {
    base64ToString(idBase64) match {
      case userIdRegex(prefix, userType, id) if userType == "User" => Some(id)
      case _                                                       => None
    }
  }

  def base64ToString(data: String): String =
    new String(decodeBase64(data))

  private[this] val userIdRegex = """^(\d*?):(.*?)(\d*)$""".r

}
