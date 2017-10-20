package controllers

import controllers.GraderController.{GradeCategory, WarningToGradeCategory}
import slick.sql.SqlAction
import testing.PostgresTestUtils

class GraderControllerSuite extends PostgresTestUtils {

  import slick.jdbc.PostgresProfile.api._

  "GraderControllerSuite" can {

    "warningsToGradeCategory" should {

      "load warnings to grade category mapping" in { _ =>
        val results: Seq[WarningToGradeCategory] = GraderController.warningsToGradeCategory.collect()
        assert(results.length === 1)

        val result = results.head
        assert(result.warning === "no-mixed-spaces-and-tabs")
        assert(result.language === "JavaScript")
        assert(result.gradeCategory === "Maintainable")
      }

    }

    "gradeCategories" should {

      "load grade categories" in { _ =>
        val results: Seq[GradeCategory] = GraderController.gradeCategories.collect()
        assert(results.length === 1)

        val result = results.head
        assert(result.gradeCategory === "Maintainable")
      }

    }

  }

  def schema: SqlAction[Int, NoStream, Effect] = sqlu"""
    CREATE TABLE IF NOT EXISTS languages (
      id SERIAL PRIMARY KEY,
      language TEXT UNIQUE NOT NULL
    );

    CREATE TABLE IF NOT EXISTS grade_categories (
      id SERIAL PRIMARY KEY,
      category TEXT UNIQUE NOT NULL
    );

    CREATE TABLE IF NOT EXISTS warnings (
      id SERIAL PRIMARY KEY,
      warning TEXT NOT NULL,
      grade_category_id INTEGER REFERENCES grade_categories NOT NULL,
      language_id INTEGER REFERENCES languages NOT NULL,
      UNIQUE (warning, grade_category_id, language_id)
    )"""

  def initialData: SqlAction[Int, NoStream, Effect] = sqlu"""
    INSERT INTO languages (id, language) VALUES
      (DEFAULT, 'JavaScript');

    INSERT INTO grade_categories (id, category) VALUES
      (DEFAULT, 'Maintainable');

    WITH
      maintainable_category AS (SELECT id FROM grade_categories WHERE category = 'Maintainable'),
      javascript_language AS (SELECT id FROM languages WHERE language = 'JavaScript')
    INSERT INTO warnings (id, warning, grade_category_id, language_id) VALUES (
      DEFAULT,
      'no-mixed-spaces-and-tabs',
      (SELECT id FROM maintainable_category),
      (SELECT id FROM javascript_language))"""

}
