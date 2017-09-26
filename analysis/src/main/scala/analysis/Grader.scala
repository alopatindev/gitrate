package gitrate.analysis

import github.GithubUser

import com.typesafe.config.Config

import java.net.URL

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Dataset, Row, SparkSession}
import org.apache.spark.SparkContext

class Grader(appConfig: Config, warningsToGradeCategory: Dataset[Row])(implicit sparkContext: SparkContext,
                                                                       sparkSession: SparkSession) {

  def grade(users: Iterable[GithubUser]): Unit = {
    import sparkSession.implicits._

    users.foreach { (user: GithubUser) =>
      // save(user) // TODO: fullName, description...
      val repositories = user.repositories.map { repo =>
        // TODO: domain as constant
        val archiveURL = new URL(s"https://github.com/${user.login}/${repo.name}/archive/${repo.defaultBranch}.tar.gz")
        val languages: String = (repo.languages.toSet + repo.primaryLanguage).mkString(",")
        s"${repo.idBase64};${archiveURL};${languages}"
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

      warningsToGradeCategory
        .join(outputMessages.filter($"messageType" === "warning"), $"tag" === $"language" && $"warning" === $"message")
        .groupBy($"idBase64", $"language", $"grade_category")
        .count()
        .select($"idBase64", $"language", $"grade_category", $"count")
        .show()
    }
  }

  private val assetsDirectory = appConfig.getString("app.assetsDir")

}
