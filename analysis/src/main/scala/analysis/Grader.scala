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

import controllers.GraderController.{GradeCategory, WarningToGradeCategory}
import github.GithubUser
import utils.LogUtils

import com.typesafe.config.Config
import java.net.URL
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{BooleanType, DoubleType, LongType}
import org.apache.spark.sql.{Dataset, Row, SparkSession}

class Grader(val appConfig: Config,
             warningsToGradeCategory: Dataset[WarningToGradeCategory],
             gradeCategories: Dataset[GradeCategory])(implicit sparkContext: SparkContext, sparkSession: SparkSession)
    extends LogUtils {

  import sparkSession.implicits._

  def processUsers(users: Seq[GithubUser]): Iterable[GradedRepository] = {
    val totalUsers = users.length
    users.zipWithIndex.flatMap {
      case (user: GithubUser, userIndex: Int) =>
        val scriptInput = user.repositories.map { repo =>
          val languages: Set[String] = repo.languages.toSet + repo.primaryLanguage
          ScriptInput(repo.idBase64, repo.name, user.login, repo.archiveURL, languages).toString
        }

        val outputMessages: Dataset[AnalyzerScriptResult] = runAnalyzerScript(scriptInput, withCleanup = true)
        val results = processAnalyzerScriptResults(outputMessages)

        logInfo(s"graded ${results.length} repositories of user ${user.login} (${userIndex + 1}/$totalUsers)")
        results
    }
  }

  case class ScriptInput(repoIdBase64: String,
                         repoName: String,
                         login: String,
                         archiveURL: URL,
                         languages: Set[String]) {

    override def toString: String = s"$repoIdBase64;$repoName;$login;$archiveURL;${languages.mkString(",")}"

  }

  def runAnalyzerScript(scriptInput: Seq[String], withCleanup: Boolean): Dataset[AnalyzerScriptResult] = {
    val scriptInputRDD: RDD[String] = sparkContext.parallelize(scriptInput)

    val maxTimeToRun = s"${maxExternalScriptDuration.getSeconds}s"
    val timeLimitedRunner = List("/bin/sh", s"$scriptsDirectory/runWithTimeout.sh", maxTimeToRun)

    val sandboxRunner = List("firejail", "--quiet", "--blacklist=/home", s"--whitelist=$scriptsDirectory")

    val scriptArguments: List[String] = List(maxRepoArchiveSizeBytes, withCleanup).map(_.toString)
    val script = s"$scriptsDirectory/downloadAndAnalyzeCode.py" :: scriptArguments

    val command: List[String] = timeLimitedRunner ++ sandboxRunner ++ script

    val scriptOutputFields = 5
    scriptInputRDD
      .pipe(command)
      .map(_.split(";"))
      .filter(_.length == scriptOutputFields)
      .map {
        case Array(idBase64, name, language, messageType, message) => (idBase64, name, language, messageType, message)
      }
      .toDF("idBase64", "name", "language", "messageType", "message")
      .as[AnalyzerScriptResult]
  }

  def processAnalyzerScriptResults(outputMessages: Dataset[AnalyzerScriptResult]): Array[GradedRepository] = {
    outputMessages.cache()

    val graded: Dataset[Row] = warningCounts(outputMessages)
      .join(linesOfCode(outputMessages), 'idBase64 === 'idBase64_ && 'name === 'name_ && 'language === 'language_)
      .select(
        'idBase64,
        'name,
        'gradeCategory,
        'linesOfCode,
        greatest(
          zero,
          one - ('warningsPerCategory.cast(DoubleType) / 'linesOfCode.cast(DoubleType))
        ) as "value"
      )
      .groupBy('idBase64, 'name, 'gradeCategory)
      .agg(sum('linesOfCode) as "linesOfCode", avg('value) as "value")
      .union(penaltiesPerAutomationGrade(outputMessages))
      .union(penaltiesPerTestableGrade(outputMessages))
      .groupBy('idBase64, 'name, 'gradeCategory)
      .agg(greatest(zero, least(one, sum('value))) as "value", sum('linesOfCode) as "linesOfCode")
      .groupBy('idBase64, 'name, 'linesOfCode)
      .agg(collect_list(struct('gradeCategory, 'value)) as "grades")

    val languageToTechnologies: Dataset[Row] = outputMessages
      .join(dependencies(outputMessages), 'idBase64 === 'idBase64_ && 'name === 'name_ && 'language === 'language_)
      .drop("idBase64_", "name_")
      .groupBy('idBase64, 'name, 'language)
      .agg(collect_set('dependencies) as "dependenciesPerLanguage")
      .select('idBase64 as "idBase64_",
              'name as "name_",
              toMapOfSeq('language, 'dependenciesPerLanguage) as "languageToTechnologies")

    val results: Dataset[GradedRepository] = graded
      .join(languageToTechnologies, 'idBase64 === 'idBase64_ && 'name === 'name_)
      .as[GradedRepository]

    results.collect()
  }

  private[this] val toMapOfSeq = udf(
    (language: String, dependencies: Seq[Seq[String]]) => Map(language -> dependencies.flatten))

  private def linesOfCode(outputMessages: Dataset[AnalyzerScriptResult]): Dataset[Row] =
    outputMessages
      .filter('messageType === "lines_of_code")
      .select('idBase64 as "idBase64_",
              'name as "name_",
              'language as "language_",
              'message.cast(LongType) as "linesOfCode")

  private def penaltiesPerAutomationGrade(outputMessages: Dataset[AnalyzerScriptResult]): Dataset[Row] = {
    val maxAutomationTools = lit(literal = 3.0)

    val initialCounts = outputMessages.select('idBase64, 'name, zeroLong as "count")

    val toolsPerRepo: Dataset[Row] = outputMessages
      .filter('messageType === "automation_tool")
      .groupBy('idBase64, 'name)
      .count()
      .select('idBase64, 'name, 'count)
      .union(initialCounts)
      .groupBy('idBase64, 'name)
      .agg(max('count) as "count")

    toolsPerRepo
      .select(
        'idBase64,
        'name,
        lit(literal = "Automated") as "gradeCategory",
        zeroLong as "linesOfCode",
        -greatest(zero, maxAutomationTools - 'count.cast(DoubleType)) / maxAutomationTools as "value"
      )
  }

  private def penaltiesPerTestableGrade(outputMessages: Dataset[AnalyzerScriptResult]): Dataset[Row] =
    outputMessages
      .filter('messageType === "tests_dir_exists")
      .select(
        'idBase64,
        'name,
        lit(literal = "Testable") as "gradeCategory",
        zeroLong as "linesOfCode",
        ('message.cast(BooleanType).cast(DoubleType) - one) as "value"
      )

  private def dependencies(outputMessages: Dataset[AnalyzerScriptResult]): Dataset[Row] =
    outputMessages
      .select('idBase64, 'name, 'language, 'message)
      .filter('messageType === "dependence")
      .groupBy('idBase64, 'name, 'language)
      .agg(collect_set('message) as "dependencies")
      .select('idBase64 as "idBase64_", 'name as "name_", 'language as "language_", 'dependencies)

  private def warningCounts(outputMessages: Dataset[AnalyzerScriptResult]): Dataset[Row] = {
    val zerosPerCategory = outputMessages
      .crossJoin(gradeCategories)
      .select('idBase64, 'name, 'language, 'gradeCategory, lit(literal = 0) as "count")

    warningsToGradeCategory
      .join(
        outputMessages.filter('messageType === "warning").withColumnRenamed("language", "language_"),
        'language === 'language_ && 'warning === 'message,
        joinType = "left"
      )
      .drop("language_")
      .groupBy('idBase64, 'name, 'language, 'gradeCategory)
      .count()
      .union(zerosPerCategory)
      .groupBy('idBase64, 'name, 'language, 'gradeCategory)
      .agg('idBase64, 'language, 'gradeCategory, max("count") as "warningsPerCategory")
  }

  private val scriptsDirectory = appConfig.getString("app.scriptsDir")
  private val maxExternalScriptDuration = appConfig.getDuration("grader.maxExternalScriptDuration")
  private val maxRepoArchiveSizeBytes = appConfig.getLong("grader.maxRepoArchiveSizeKiB") * 1024L

  private val zeroLong = lit(literal = 0L)
  private val zero = lit(literal = 0.0)
  private val one = lit(literal = 1.0)

}

case class Grade(gradeCategory: String, value: Double)
case class GradedRepository(idBase64: String,
                            name: String,
                            languageToTechnologies: Map[String, Seq[String]],
                            grades: Seq[Grade],
                            linesOfCode: Long)

case class AnalyzerScriptResult(idBase64: String, name: String, language: String, messageType: String, message: String)
