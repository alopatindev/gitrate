package analysis

import analysis.TextAnalyzer.StemToSynonyms

import org.scalatest.Matchers._
import org.scalatest.WordSpec

class TextAnalyzerSpec extends WordSpec {

  "technologySynonyms" should {

    "detect synonyms for languages that support package manager" in {
      val input = Map("JavaScript" -> Seq("eslint-plugin-better", "eslint", "nodemon"),
                      "C++" -> Seq("STL", "OpenCL", "OpenCL-extensions"))
      val result = TextAnalyzer.technologySynonyms(input)
      def synonyms(language: String): Option[StemToSynonyms] =
        result
          .find { case (lang, _) => lang == language }
          .map { case (_, stemToSynonyms) => stemToSynonyms }
      assert(synonyms("JavaScript").get("eslint") === Set("eslint-plugin-better"))
      assert(synonyms("C++").get("OpenCL") === Set[String]())
    }

    "ignore synonyms without delimiters" in {
      val input = Map("JavaScript" -> Seq("eslint-plugin-better", "eslint", "preact", "eslint_d", "react-dom", "react"))
      val result = TextAnalyzer.technologySynonyms(input)
      def synonyms(language: String): Option[StemToSynonyms] =
        result
          .find { case (lang, _) => lang == language }
          .map { case (_, stemToSynonyms) => stemToSynonyms }
      assert(synonyms("JavaScript").get("eslint") === Set("eslint-plugin-better", "eslint_d"))
      assert(synonyms("JavaScript").get("react") === Set("react-dom"))
      assert(synonyms("JavaScript").get("preact") === Set())
    }

  }

}
