package hiregooddevs.analysis.github

import com.datastax.driver.core.Row

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

object GithubSearchQuery {

  def apply(row: Row): GithubSearchQuery =
    new GithubSearchQuery(
      row.getString("language"),
      row.getString("filename"),
      row.getInt("minRepoSizeKiB"),
      row.getInt("maxRepoSizeKiB"),
      row.getInt("minStars"),
      row.getInt("maxStars"),
      row.getString("pattern")
    )

}
