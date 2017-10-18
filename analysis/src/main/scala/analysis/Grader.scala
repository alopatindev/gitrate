package analysis

import github.GithubUser

import com.typesafe.config.Config

import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.DoubleType
import org.apache.spark.sql.{Dataset, Row, SparkSession}

import java.net.URL

class Grader(val appConfig: Config,
             warningsToGradeCategory: Dataset[WarningToGradeCategory],
             gradeCategories: Dataset[GradeCategory])(implicit sparkContext: SparkContext, sparkSession: SparkSession) {

  import sparkSession.implicits._

  def gradeUsers(users: Iterable[GithubUser]): Iterable[GradedRepository] = {
    val resultsByRepository
      : Map[String, Iterable[GraderResult]] = processUsers(users).groupBy(_.idBase64) // FIXME: remove groupBy?
    for {
      (idBase64, results) <- resultsByRepository
      grades = results.map(_.toGrade).toSeq
      languageToTechnologies: Map[String, Set[String]] = results
        .flatMap(_.languageToTechnologies)
        .toMap // FIXME: possible loss of technologies
      name: String = results.head.name // FIXME
      linesOfCode = results.head.linesOfCode
    } yield GradedRepository(idBase64, name, languageToTechnologies, grades, linesOfCode)
  }

  private def processUsers(users: Iterable[GithubUser]): Iterable[GraderResult] = {
    users.flatMap { user: GithubUser =>
      val scriptInput = user.repositories.map { repo =>
        val languages: Set[String] = repo.languages.toSet + repo.primaryLanguage
        ScriptInput(repo.idBase64, repo.name, user.login, repo.archiveURL, languages).toString
      }

      val outputMessages: Dataset[AnalyzerScriptResult] = runAnalyzerScript(scriptInput, withCleanup = true)
      processAnalyzerScriptResults(outputMessages)
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
    val timeLimitedRunner = List(s"$scriptsDirectory/runWithTimeout.sh", maxTimeToRun)

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

  def processAnalyzerScriptResults(outputMessages: Dataset[AnalyzerScriptResult]): Iterable[GraderResult] = {
    outputMessages.cache()

    val zero = lit(literal = 0.0)
    val one = lit(literal = 1.0)
    val graded: Dataset[Row] = warningCounts(outputMessages)
      .join(linesOfCode(outputMessages),
            $"idBase64" === $"idBase64_" && $"name" === $"name_" && $"language" === $"language_")
      .drop("idBase64_", "name_", "language_")
      .select(
        $"idBase64",
        $"name",
        $"gradeCategory",
        $"warningsPerCategory".cast(DoubleType) as "warningsPerCategory",
        $"linesOfCode".cast(DoubleType) as "linesOfCode"
      )
      .select(
        $"idBase64",
        $"name",
        $"gradeCategory",
        $"linesOfCode",
        greatest(
          zero,
          one - ($"warningsPerCategory" / $"linesOfCode")
        ) as "value"
      )
      .groupBy($"idBase64", $"name", $"gradeCategory")
      .agg(sum($"linesOfCode") as "linesOfCode", avg($"value") as "value")

    val languageToDependencies: Dataset[Row] = outputMessages
      .join(dependencies(outputMessages),
            $"idBase64" === $"idBase64_" && $"name" === $"name_" && $"language" === $"language_")
      .drop("idBase64_", "name_")
      .groupBy($"idBase64", $"name", $"language")
      .agg(collect_set($"dependencies") as "dependenciesPerLanguage")
      .select($"idBase64" as "idBase64_",
              $"name" as "name_",
              makeMap($"language", $"dependenciesPerLanguage") as "languageToDependencies")

    val results: Dataset[PartialGraderResult] = graded
      .join(languageToDependencies, $"idBase64" === $"idBase64_" && $"name" === $"name_")
      .as[PartialGraderResult]

    results.show(truncate = false)

    results
      .collect()
      .map(_.toGraderResult)
  }

  private def linesOfCode(outputMessages: Dataset[AnalyzerScriptResult]): Dataset[Row] =
    outputMessages
      .filter($"messageType" === "lines_of_code")
      .select($"idBase64" as "idBase64_", $"name" as "name_", $"language" as "language_", $"message" as "linesOfCode")

  private val makeMap = udf((language: String, dependencies: Seq[Seq[String]]) => Map(language -> dependencies.flatten))

  private def dependencies(outputMessages: Dataset[AnalyzerScriptResult]): Dataset[Row] =
    outputMessages
      .select($"idBase64", $"name", $"language", $"message")
      .filter($"messageType" === "dependence")
      .groupBy($"idBase64", $"name", $"language")
      .agg(collect_set($"message") as "dependencies")
      .select($"idBase64" as "idBase64_", $"name" as "name_", $"language" as "language_", $"dependencies")

  private def warningCounts(outputMessages: Dataset[AnalyzerScriptResult]): Dataset[Row] = {
    val zerosPerCategory = outputMessages
      .crossJoin(gradeCategories)
      .select(
        $"idBase64",
        $"name",
        $"language",
        $"gradeCategory",
        lit(literal = 0) as "count"
      )
      .distinct

    warningsToGradeCategory
      .join(
        outputMessages.filter($"messageType" === "warning").withColumnRenamed("language", "language_"),
        $"language" === $"language_" && $"warning" === $"message",
        joinType = "left"
      )
      .drop("language_")
      .groupBy($"idBase64", $"name", $"language", $"gradeCategory")
      .count()
      .union(zerosPerCategory)
      .groupBy($"idBase64", $"name", $"language", $"gradeCategory")
      .agg($"idBase64", $"language", $"gradeCategory", max("count") as "warningsPerCategory")
  }

  private val scriptsDirectory = appConfig.getString("app.scriptsDir")
  private val maxExternalScriptDuration = appConfig.getDuration("grader.maxExternalScriptDuration")
  private val maxRepoArchiveSizeBytes = appConfig.getInt("grader.maxRepoArchiveSizeKiB") * 1024

}

case class GradeCategory(gradeCategory: String)
case class WarningToGradeCategory(warning: String, language: String, gradeCategory: String)

case class Grade(gradeCategory: String, value: Double)
case class GradedRepository(idBase64: String,
                            name: String,
                            languageToTechnologies: Map[String, Set[String]],
                            grades: Seq[Grade],
                            linesOfCode: Int)

case class PartialGraderResult(idBase64: String,
                               name: String,
                               languageToDependencies: Map[String, Seq[String]],
                               gradeCategory: String,
                               linesOfCode: Double,
                               value: Double) {

  def toGraderResult: GraderResult =
    GraderResult(idBase64, name, languageToDependencies.mapValues(_.toSet), gradeCategory, linesOfCode.toInt, value)

}

case class GraderResult(idBase64: String,
                        name: String,
                        languageToTechnologies: Map[String, Set[String]],
                        gradeCategory: String,
                        linesOfCode: Int,
                        value: Double) {

  def toGrade: Grade = Grade(gradeCategory, value)

}

case class AnalyzerScriptResult(idBase64: String, name: String, language: String, messageType: String, message: String)
