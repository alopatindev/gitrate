package gitrate.analysis

package object github {

  import java.util.Date

  case class GithubRepo(val idBase64: String,
                        val name: String,
                        val created: Date,
                        val updated: Date,
                        val primaryLanguage: String,
                        val languages: Seq[String],
                        val isFork: Boolean,
                        val isMirror: Boolean) {
    val isOld: Boolean = ???
    val hasMostlyCommitsByOwner: Boolean = ???
    val userIsOwner: Boolean = ???
    val isLanguageSupported: Boolean = ???
    val isTarget: Boolean = ???
  }

  case class GithubUser(val login: String) {
    val email: Option[String] = ???
    def repo(name: String): GithubRepo = ???
    def repos: Seq[GithubRepo] = ???
    def targetRepos: Seq[GithubRepo] = ???
    val isValid: Boolean = ???
    val isTarget: Boolean = ???
  }

}
