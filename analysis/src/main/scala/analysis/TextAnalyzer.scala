// Copyright 2017 Alexander Lopatin
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package analysis

import scala.annotation.tailrec

object TextAnalyzer {

  type Synonyms = Set[String]
  type StemToSynonyms = Map[String, Synonyms]

  def technologySynonyms(languageToTechnologies: Map[String, Seq[String]]): Iterable[(String, StemToSynonyms)] =
    for {
      (language: String, technologies: Seq[String]) <- languageToTechnologies
      technologyToSynonyms: StemToSynonyms = if (languageSupportsPackageManager contains language) {
        stem(technologies)
      } else { technologies.map(_ -> Set[String]()).toMap }
    } yield (language, technologyToSynonyms)

  private def stem(xs: Seq[String]): StemToSynonyms = {
    @tailrec
    def helper(xs: Seq[String], acc: StemToSynonyms): StemToSynonyms = xs match {
      case x :: tail =>
        val synonymsTail: Synonyms = tail.filter(item => (item contains x) && item.exists(delimiters contains _)).toSet
        val newSynonyms: Synonyms = acc.getOrElse(x, Set()) ++ synonymsTail
        val newTail: Seq[String] = tail.filterNot(synonymsTail.contains)
        val newAcc: StemToSynonyms = acc + (x -> newSynonyms)
        helper(newTail, newAcc)
      case Nil => acc
    }

    val (input, skippedInput) = xs.splitAt(maxTechnologiesToStem)
    val ignoredStems: Synonyms = (input.filter(_.length < minStemLength) ++ skippedInput).toSet
    val ignoreResult: StemToSynonyms = ignoredStems.map(_ -> Set[String]()).toMap
    val sortedInput: Seq[String] = input
      .filterNot(ignoredStems.contains)
      .sortBy(_.length)
      .toList // workaround for MatchError
    val result = helper(sortedInput, Map())
    result ++ ignoreResult
  }

  private val languageSupportsPackageManager = Set("JavaScript")
  private val minStemLength = 4
  private val maxTechnologiesToStem = 1000
  private val delimiters = "-_."

}
