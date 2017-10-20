package controllers

import org.apache.spark.sql.Dataset
import utils.{AppConfig, ResourceUtils, SparkUtils}

object GraderController extends AppConfig with ResourceUtils with SparkUtils {

  @transient lazy val warningsToGradeCategory: Dataset[WarningToGradeCategory] = {
    val sparkSession = getOrCreateSparkSession()
    import sparkSession.implicits._

    Postgres
      .executeSQL(resourceToString("/db/loadWarningsToGradeCategory.sql"))
      .as[WarningToGradeCategory]
      .cache()
  }

  @transient lazy val gradeCategories: Dataset[GradeCategory] = {
    val sparkSession = getOrCreateSparkSession()
    import sparkSession.implicits._

    Postgres
      .executeSQL("SELECT category AS gradeCategory FROM grade_categories")
      .as[GradeCategory]
      .cache()
  }

  case class GradeCategory(gradeCategory: String)
  case class WarningToGradeCategory(warning: String, language: String, gradeCategory: String)

}
