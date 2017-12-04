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
