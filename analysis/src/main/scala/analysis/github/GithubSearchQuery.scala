package hiregooddevs.analysis.github

case class GithubSearchQuery(
    val language: String,
    val filename: String,
    val maxRepoSizeKiB: Int, // TODO: KiB implicit conversion?
    val pattern: String = "") {

  val sort = "updated"
  val fork = false
  val mirror = false

  override def toString =
    s"language:$language in:$filename sort:$sort mirror:$mirror fork:$fork size:<=$maxRepoSizeKiB $pattern"

}
