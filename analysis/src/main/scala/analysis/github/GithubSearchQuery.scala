package hiregooddevs.analysis.github

case class GithubSearchQuery(val language: String,
                             val filename: String,
                             val minRepoSizeKiB: Int,
                             val maxRepoSizeKiB: Int,
                             val pattern: String = "") {

  val sort = "updated"
  val fork = false
  val mirror = false

  override def toString: String =
    s"language:$language in:$filename sort:$sort mirror:$mirror fork:$fork size:$minRepoSizeKiB..$maxRepoSizeKiB $pattern"

}
