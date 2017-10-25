package analysis

import scala.annotation.tailrec

object TextAnalyzer {

  type Synonyms = Set[String]
  type StemToSynonyms = Map[String, Synonyms]

  def technologySynonyms(languageToTechnologies: Map[String, Seq[String]]): Iterable[(String, StemToSynonyms)] =
    for {
      (language, technologies) <- languageToTechnologies
      if languageSupportsPackageManager contains language
      technologyToSynonyms = stem(technologies, minLength = minStemLength, limit = maxTechnologiesToStem)
    } yield (language, technologyToSynonyms)

  def stem(xs: Seq[String], minLength: Int, limit: Int): StemToSynonyms = {
    @tailrec
    def helper(xs: Seq[String], acc: StemToSynonyms): StemToSynonyms = xs match {
      case x :: tail =>
        val synonymsTail: Synonyms = tail.filter(_ contains x).toSet
        val newSynonyms: Synonyms = acc.getOrElse(x, Set()) ++ synonymsTail
        val newTail: Seq[String] = tail.filterNot(synonymsTail.contains)
        val newAcc: StemToSynonyms = acc + (x -> newSynonyms)
        helper(newTail, newAcc)
      case Nil => acc
    }

    val (input, ignoredInput) = xs.splitAt(limit)
    val ignoredStems: Synonyms = (input.filter(_.length < minLength) ++ ignoredInput).toSet
    val ignoreResult: StemToSynonyms = ignoredStems.map(_ -> Set[String]()).toMap
    val sortedXs: Seq[String] = input.filterNot(ignoredStems.contains).sortBy(_.length)
    val result = helper(sortedXs, Map())
    result ++ ignoreResult
  }

  private val languageSupportsPackageManager = Set("JavaScript")
  private val minStemLength = 4
  private val maxTechnologiesToStem = 1000

}
