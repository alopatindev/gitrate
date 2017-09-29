package gitrate.analysis

import github.GithubUser

import com.typesafe.config.Config

import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext
import org.apache.spark.sql.functions.{avg, greatest, lit}
import org.apache.spark.sql.types.DoubleType
import org.apache.spark.sql.{Dataset, Row, SparkSession}

class Grader(appConfig: Config, warningsToGradeCategory: Dataset[Row])(implicit sparkContext: SparkContext,
                                                                       sparkSession: SparkSession) {

  def gradeGithubUsers(users: Iterable[GithubUser]): Iterable[GradedRepository] = {
    val resultsByRepository: Map[String, Iterable[GraderResult]] = processGithubUsers(users).groupBy(_.idBase64)
    for {
      (idBase64, results) <- resultsByRepository
      grades = results.map(_.toGrade)
    } yield GradedRepository(idBase64, grades.toSeq)
  }

  private def processGithubUsers(users: Iterable[GithubUser]): Iterable[GraderResult] = {
    import sparkSession.implicits._

    users.flatMap { user: GithubUser =>
      val repositories = user.repositories.map { repo =>
        val languages: String = (repo.languages.toSet + repo.primaryLanguage).mkString(",")
        s"${repo.idBase64};${repo.archiveURL};${languages}"
      }

      val repositoriesRDD: RDD[String] = sparkContext
        .parallelize(repositories.toSeq)

      val outputFields = 4
      val outputMessages: Dataset[Row] = repositoriesRDD
        .pipe(
          Seq("firejail",
              "--quiet",
              "--blacklist=/home",
              s"--whitelist=${assetsDirectory}",
              s"${assetsDirectory}/downloadAndAnalyzeCode.sh"))
        .map(_.split(";"))
        .filter(_.length == outputFields)
        .map {
          case Array(idBase64, language, messageType, message) => (idBase64, language, messageType, message)
        }
        .toDF("idBase64", "language", "messageType", "message")
        .cache()

      val warningCounts = warningsToGradeCategory
        .join(outputMessages.filter($"messageType" === "warning"), $"tag" === $"language" && $"warning" === $"message")
        .groupBy($"idBase64", $"language", $"grade_category")
        .count()
        .select($"idBase64", $"language", $"grade_category" as "gradeCategory", $"count" as "warningsPerCategory")

      val linesOfCode = outputMessages
        .filter($"messageType" === "lines_of_code")
        .select($"idBase64" as "idBase64_", $"language" as "language_", $"message" as "linesOfCode")

      val graderResults: Dataset[GraderResult] = warningCounts
        .join(linesOfCode, $"idBase64" === $"idBase64_" && $"language" === $"language_")
        .select(
          $"idBase64",
          $"language",
          $"gradeCategory",
          (greatest(lit(0.0), lit(1.0) - ($"warningsPerCategory".cast(DoubleType) / $"linesOfCode".cast(DoubleType)))) as "value"
        )
        .distinct
        .drop("language")
        .groupBy($"idBase64", $"gradeCategory")
        .agg(avg($"value") as "value")
        .as[GraderResult]

      graderResults.show(truncate = false)
      graderResults.collect()
    }
  }

  private val assetsDirectory = appConfig.getString("app.assetsDir")

}

case class Grade(val gradeCategory: String, val value: Double)
case class GradedRepository(val idBase64: String, val grades: Seq[Grade])

case class GraderResult(val idBase64: String, val gradeCategory: String, val value: Double) {
  def toGrade = Grade(gradeCategory, value)
}
