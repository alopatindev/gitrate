package gitrate.analysis

import github.GithubUser

import com.typesafe.config.Config

import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.DoubleType
import org.apache.spark.sql.{Dataset, Row, SparkSession}

class Grader(val appConfig: Config,
             warningsToGradeCategory: Dataset[WarningToGradeCategory],
             weightedTechnologies: Seq[String])(implicit sparkContext: SparkContext, sparkSession: SparkSession) {

  import sparkSession.implicits._

  def gradeUsers(users: Iterable[GithubUser]): Iterable[GradedRepository] = {
    val resultsByRepository: Map[String, Iterable[GraderResult]] = processUsers(users).groupBy(_.idBase64)
    for {
      (idBase64, results) <- resultsByRepository
      grades = results.map(_.toGrade).toSeq
      tags = results.flatMap(_.tags).toSet
    } yield GradedRepository(idBase64, tags, grades)
  }

  private def processUsers(users: Iterable[GithubUser]): Iterable[GraderResult] = {
    users.flatMap { user: GithubUser =>
      val scriptInput = user.repositories.map { repo =>
        val languages: String = (repo.languages.toSet + repo.primaryLanguage).mkString(",")
        s"${repo.idBase64};${repo.archiveURL};${languages}"
      }

      val outputMessages: Dataset[AnalyzerScriptResult] = runAnalyzerScript(scriptInput, withCleanup = true)
      processAnalyzerScriptResults(outputMessages)
    }
  }

  def runAnalyzerScript(scriptInput: Seq[String], withCleanup: Boolean): Dataset[AnalyzerScriptResult] = {
    val scriptInputRDD: RDD[String] = sparkContext.parallelize(scriptInput)

    val firejailArguments = List("--quiet",
                                 "--blacklist=/home",
                                 s"--whitelist=${assetsDirectory}",
                                 s"${assetsDirectory}/downloadAndAnalyzeCode.sh")

    val scriptArguments: List[String] =
      if (withCleanup) List("--with-cleanup")
      else List()

    val scriptOutputFields = 4
    scriptInputRDD
      .pipe("firejail" :: firejailArguments ++ scriptArguments)
      .map(_.split(";"))
      .filter(_.length == scriptOutputFields)
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
      .agg(collect_set($"message") as "dependencies")
      .select($"idBase64" as "idBase64_", $"language" as "language_", $"dependencies")

    val zerosPerCategory = outputMessages
      .select(
        $"idBase64",
        $"language",
        explode(split(lit("Maintainable,Testable,Robust,Secure,Automated,Performant"), ",")) as "gradeCategory", // TODO: load from database?
        lit(0) as "count"
      )
      .distinct

    val warningCounts = warningsToGradeCategory
      .join(outputMessages.filter($"messageType" === "warning"),
            $"tag" === $"language" && $"warning" === $"message",
            joinType = "left")
      .groupBy($"idBase64", $"language", $"gradeCategory")
      .count()
      .union(zerosPerCategory)
      .groupBy($"idBase64", $"language", $"gradeCategory")
      .agg($"idBase64", $"language", $"gradeCategory", max("count") as "warningsPerCategory")

    val linesOfCode = outputMessages
      .filter($"messageType" === "lines_of_code")
      .select($"idBase64" as "idBase64_", $"language" as "language_", $"message" as "linesOfCode")

    val results: Dataset[PartialGraderResult] = warningCounts
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
      .as[PartialGraderResult]

    results.show(truncate = false)
    results.collect().map(_.toGraderResult(weightedTechnologies))
  }

  private val assetsDirectory = appConfig.getString("app.assetsDir")

}

case class WarningToGradeCategory(val warning: String, val tag: String, val gradeCategory: String)

case class Grade(val gradeCategory: String, val value: Double)
case class GradedRepository(val idBase64: String, val tags: Set[String], val grades: Seq[Grade])

case class PartialGraderResult(val idBase64: String,
                               dependencies: Seq[String],
                               val gradeCategory: String,
                               val value: Double) {

  def toGraderResult(weightedTechnologies: Seq[String]): GraderResult = {
    def dependenceToTag(dependence: String): String =
      weightedTechnologies
        .filter(technology => dependence.toLowerCase contains technology.toLowerCase)
        .headOption
        .getOrElse(dependence)
    val tags = dependencies.map(dependenceToTag).toSet
    GraderResult(idBase64, tags, gradeCategory, value)
  }

}

case class GraderResult(val idBase64: String, val tags: Set[String], val gradeCategory: String, val value: Double) {

  def toGrade = Grade(gradeCategory, value)

}

case class AnalyzerScriptResult(val idBase64: String,
                                val language: String,
                                val messageType: String,
                                val message: String)
