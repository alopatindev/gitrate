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

package controllers

import utils.{AppConfig, ResourceUtils, SparkUtils}

import org.apache.spark.sql.Dataset

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
