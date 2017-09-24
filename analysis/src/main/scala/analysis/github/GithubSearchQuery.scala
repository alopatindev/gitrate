package gitrate.analysis.github

case class GithubSearchQuery(val language: String,
                             val filename: String,
                             val minRepoSizeKiB: Int,
                             val maxRepoSizeKiB: Int,
                             val minStars: Int,
                             val maxStars: Int,
                             val pattern: String) {

  val sort = "updated"
  val fork = false
  val mirror = false

  override def toString: String =
    s"language:$language in:$filename sort:$sort mirror:$mirror fork:$fork " +
      s"size:$minRepoSizeKiB..$maxRepoSizeKiB stars:$minStars..$maxStars $pattern"

}
