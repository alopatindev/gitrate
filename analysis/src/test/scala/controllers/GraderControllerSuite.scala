package controllers

import controllers.GraderController.{GradeCategory, WarningToGradeCategory}
import testing.PostgresTestUtils

class GraderControllerSuite extends PostgresTestUtils {

  "GraderControllerSuite" can {

    "warningsToGradeCategory" should {

      "load warnings to grade category mapping" in { _ =>
        val results: Seq[WarningToGradeCategory] = GraderController.warningsToGradeCategory.collect()
        val expected = WarningToGradeCategory(warning = "no-mixed-spaces-and-tabs",
                                              language = "JavaScript",
                                              gradeCategory = "Maintainable")
        assert(results contains expected)
      }

    }

    "gradeCategories" should {

      "load grade categories" in { _ =>
        val results: Seq[GradeCategory] = GraderController.gradeCategories.collect()
        assert(results contains GradeCategory(gradeCategory = "Maintainable"))
      }

    }

  }

}
