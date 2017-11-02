package analysis

import analysis.TextAnalyzer.{Location, StemToSynonyms}

import org.scalatest.Matchers._
import org.scalatest.WordSpec

class TextAnalyzerSuite extends WordSpec {

  "TextAnalyzerSuite" can {

    "parseLocation" should {

      "process empty input" in {
        assert(TextAnalyzer.parseLocation("") === Location(country = None, city = None))
      }

      "parse geo locations" in {
        assert(
          TextAnalyzer.parseLocation("Saint Petersburg, Russia") === Location(country = Some("Russian Federation"),
                                                                              city = Some("Saint Petersburg")))
        assert(TextAnalyzer.parseLocation("Russia") === Location(country = Some("Russian Federation"), city = None))
        assert(TextAnalyzer.parseLocation("CA") === Location(country = Some("United States"), city = None))
      }

      "process only one location" in {
        val input = "united states saint petersburg russian federation"
        assert(
          TextAnalyzer.parseLocation(input) === Location(country = Some("Russian Federation"),
                                                         city = Some("Saint Petersburg")))
      }

    }

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
        assert(synonyms("C++").isEmpty)
      }

    }

    "stem" should {

      "build tree of stems to synonyms" in {
        val input = Seq("programming", "variance", "invariant", "variant", "variants", "prog", "metal")
        val result = TextAnalyzer.stem(input, minLength = 2, limit = 10)
        val expected = Map("prog" -> Set("programming"),
                           "variant" -> Set("invariant", "variants"),
                           "variance" -> Set(),
                           "metal" -> Set())
        result shouldEqual expected
      }

      "ignore too short words" in {
        val input = Seq("programming", "variance", "invariant", "variant", "variants", "prog", "metal")
        val result = TextAnalyzer.stem(input, minLength = 5, limit = 10)
        val expected = Map("variant" -> Set("invariant", "variants"),
                           "variance" -> Set(),
                           "prog" -> Set(),
                           "programming" -> Set(),
                           "metal" -> Set())
        result shouldEqual expected
      }

      "ignore items when out of limit" in {
        val input = Seq("programming", "variance", "invariant", "variant", "variants", "prog", "metal")
        val result = TextAnalyzer.stem(input, minLength = 2, limit = 4)
        val expected = Map("programming" -> Set(),
                           "variant" -> Set("invariant"),
                           "variants" -> Set(),
                           "variance" -> Set(),
                           "prog" -> Set(),
                           "metal" -> Set())
        result shouldEqual expected
      }

    }

  }

}
