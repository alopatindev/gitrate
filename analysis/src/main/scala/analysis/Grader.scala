package gitrate.analysis

import github.GithubUser

import com.typesafe.config.Config

import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.DoubleType
import org.apache.spark.sql.{Dataset, Row, SparkSession}

// TODO: Dataset[type]
class Grader(val appConfig: Config, warningsToGradeCategory: Dataset[Row])(implicit sparkContext: SparkContext,
                                                                           sparkSession: SparkSession) {

  import sparkSession.implicits._

  def gradeGithubUsers(users: Iterable[GithubUser]): Iterable[GradedRepository] = {
    val resultsByRepository: Map[String, Iterable[GraderResult]] = processGithubUsers(users).groupBy(_.idBase64)
    for {
      (idBase64, results) <- resultsByRepository
      grades = results.map(_.toGrade)
      dependencies = results.flatMap(_.dependencies)
    } yield GradedRepository(idBase64, dependencies.toSeq, grades.toSeq) // TODO: remove toSeq?
  }

  private def processGithubUsers(users: Iterable[GithubUser]): Iterable[GraderResult] = {
    users.flatMap { user: GithubUser =>
      val scriptInput = user.repositories.map { repo =>
        val languages: String = (repo.languages.toSet + repo.primaryLanguage).mkString(",")
        s"${repo.idBase64};${repo.archiveURL};${languages}"
      }

      val outputMessages: Dataset[AnalyzerScriptResult] = runAnalyzerScript(scriptInput)
      processAnalyzerScriptResults(outputMessages)
    }
  }

  def runAnalyzerScript(scriptInput: Seq[String]): Dataset[AnalyzerScriptResult] = {
    val scriptInputRDD: RDD[String] = sparkContext.parallelize(scriptInput)
    val outputFields = 4
    scriptInputRDD
      .pipe(
        Seq("firejail",
            "--quiet",
            "--blacklist=/home",
            s"--whitelist=${assetsDirectory}",
            s"${assetsDirectory}/downloadAndAnalyzeCode.sh"))
      .map(_.split(";"))
      .filter(_.length == outputFields)
      .map { case Array(idBase64, language, messageType, message) => (idBase64, language, messageType, message) }
      .toDF("idBase64", "language", "messageType", "message")
      .as[AnalyzerScriptResult]
  }

  def processAnalyzerScriptResults(outputMessages: Dataset[AnalyzerScriptResult]): Iterable[GraderResult] = {
    outputMessages.cache()

    val dependencies = outputMessages
      .select($"idBase64", $"language", $"message")
      .filter($"messageType" === "dependence")
      .groupBy($"idBase64", $"language")
      .agg(collect_list($"message") as "dependencies")
      .select($"idBase64" as "idBase64_", $"language" as "language_", $"dependencies")

    val zerosPerCategory = outputMessages
      .select($"idBase64",
              $"language",
              explode(split(lit("Robust,Maintainable"), ",")) as "grade_category", // TODO: load from database?
              lit(0) as "count")
      .distinct

    val warningCounts = warningsToGradeCategory
      .join(outputMessages.filter($"messageType" === "warning"),
            $"tag" === $"language" && $"warning" === $"message",
            joinType = "left")
      .groupBy($"idBase64", $"language", $"grade_category")
      .count()
      .union(zerosPerCategory)
      .groupBy($"idBase64", $"language", $"grade_category")
      .agg($"idBase64", $"language", $"grade_category" as "gradeCategory", max("count") as "warningsPerCategory")

    val linesOfCode = outputMessages
      .filter($"messageType" === "lines_of_code")
      .select($"idBase64" as "idBase64_", $"language" as "language_", $"message" as "linesOfCode")

    val graderResults: Dataset[GraderResult] = warningCounts
      .join(linesOfCode, $"idBase64" === $"idBase64_" && $"language" === $"language_")
      .drop("idBase64_", "language_")
      .join(dependencies, $"idBase64" === $"idBase64_" && $"language" === $"language_")
      .select(
        $"idBase64",
        $"language",
        $"dependencies",
        $"gradeCategory",
        (greatest(lit(0.0), lit(1.0) - ($"warningsPerCategory".cast(DoubleType) / $"linesOfCode".cast(DoubleType)))) as "value"
      )
      .distinct
      .drop("language")
      .groupBy($"idBase64", $"gradeCategory", $"dependencies")
      .agg(avg($"value") as "value")
      .as[GraderResult]

    graderResults.show(truncate = false)
    graderResults.collect()
  }

  private val assetsDirectory = appConfig.getString("app.assetsDir")

}

case class Grade(val gradeCategory: String, val value: Double)
case class GradedRepository(val idBase64: String, val dependencies: Seq[String], val grades: Seq[Grade])

case class GraderResult(val idBase64: String,
                        val dependencies: Seq[String],
                        val gradeCategory: String,
                        val value: Double) {
  def toGrade = Grade(gradeCategory, value)
}

case class AnalyzerScriptResult(val idBase64: String,
                                val language: String,
                                val messageType: String,
                                val message: String)
