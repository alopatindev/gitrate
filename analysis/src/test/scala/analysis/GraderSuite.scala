package gitrate.analysis

import gitrate.utils.TestUtils

import com.holdenkarau.spark.testing.DataFrameSuiteBase

import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.{Dataset, SparkSession}
import org.scalatest.{fixture, Outcome}

class GraderSuite extends fixture.WordSpec with DataFrameSuiteBase with TestUtils {

  import com.typesafe.config.ConfigFactory
  import scala.io.Source
  import java.io.File
  import java.util.UUID

  "Grader" can {

    "runAnalyzerScript" should {

      "ignore repositories that contain files or directories with bad names" in { fixture =>
        val login = "himanshuchandra"
        val repoName = "react.js-codes"
        val language = "JavaScript"
        val (results, _, _, repoId) = fixture.runAnalyzerScript(login, repoName, language)
        val invalidResults = results.collect().filter(_.idBase64 == repoId)
        assert(invalidResults.isEmpty)
      }

      "remove temporary files when done" in { fixture =>
        val login = "alopatindev"
        val repoName = "find-telegram-bot"
        val language = "JavaScript"
        val (results, pathExists, _, _) = fixture.runAnalyzerScript(login, repoName, language)
        val _ = results.collect()
        val directoryExists = pathExists("/")
        assert(!directoryExists)
      }

      "process multiple languages" in { fixture =>
        ???
      }

      "ignore repository if script fails" in { fixture =>
        ???
      }

    }

    "JavaScript analysis" should {

      "compute lines of code" in { fixture =>
        val login = "alopatindev"
        val repoName = "find-telegram-bot"
        val language = "JavaScript"
        val (results, _, _, _) = fixture.runAnalyzerScript(login, repoName, language)
        val linesOfCode: Option[Int] = results
          .collect()
          .filter(result => result.language == language && result.messageType == "lines_of_code")
          .headOption
          .map(_.message.toInt)
        assert(linesOfCode.isDefined && linesOfCode.get > 0)
      }

      "detect dependencies" in { fixture =>
        val login = "alopatindev"
        val repoName = "find-telegram-bot"
        val language = "JavaScript"
        val (results, _, _, _) = fixture.runAnalyzerScript(login, repoName, language)
        val dependencies: Set[String] = results
          .collect()
          .filter(result => result.language == language && result.messageType == "dependence")
          .map(_.message)
          .toSet
        assert(dependencies contains "phantom")
        assert(dependencies contains "eslint")
        assert(dependencies contains "eslint-plugin-promise")
        assert(!(dependencies contains "PhantomJS"))
      }

      "detect Node.js dependence" in { fixture =>
        def hasDependence(login: String, repoName: String): Boolean = {
          val language = "JavaScript"
          val (results, _, _, _) = fixture.runAnalyzerScript(login, repoName, language)
          val dependencies: Set[String] = results
            .collect()
            .filter(result => result.language == language && result.messageType == "dependence")
            .map(_.message)
            .toSet
          dependencies contains "Node.js"
        }

        assert(hasDependence(login = "alopatindev", repoName = "find-telegram-bot"))
        assert(hasDependence(login = "gorillamania", repoName = "package.json-validator"))
        assert(!hasDependence(login = "jquery", repoName = "jquery.com"))
      }

      "process absent dependencies" in { fixture =>
        ???
      }

      "remove node_modules, package.json, bower.json, *.eslint*, yarn.lock" in { fixture =>
        val login = "alopatindev"
        val repoName = "find-telegram-bot"
        val language = "JavaScript"
        val (results, pathExists, _, _) = fixture.runAnalyzerScript(login, repoName, language, withCleanup = false)
        val _ = results.collect()
        assert(!pathExists("/node_modules"))
        assert(!pathExists("/package.json"))
        assert(!pathExists("/bower.json"))
        assert(!pathExists("/.eslintrc.yml"))
        assert(!pathExists("/yarn.lock"))
      }

      "remove minified files" in { fixture =>
        val login = "Masth0"
        val repoName = "TextRandom"
        val language = "JavaScript"
        val (results, pathExists, _, _) = fixture.runAnalyzerScript(login, repoName, language, withCleanup = false)
        val _ = results.collect()
        assert(!pathExists("/dist/TextRandom.min.js"))
      }

      "remove third-party libraries" in { fixture =>
        ???
      }

      "remove comments" in { fixture =>
        val login = "Masth0"
        val repoName = "TextRandom"
        val language = "JavaScript"
        val (results, _, fileContainsText, _) =
          fixture.runAnalyzerScript(login, repoName, language, withCleanup = false)
        val _ = results.collect()
        assert(!fileContainsText("/src/TextRandom.js", "Create or remove span"))
        assert(!fileContainsText("/src/TextRandom.js", "Animate random characters"))
      }

    }

    "processAnalyzerScriptResults" should {

      "ignore dependence of dependence" in { fixture =>
        val login = "alopatindev"
        val repoName = "find-telegram-bot"
        val language = "JavaScript"
        val (results: Iterable[GraderResult], _, _, _) =
          fixture.processAnalyzerScriptResults(login, repoName, language)
        val tags = results.head.tags
        assert(tags contains "ESLint")
        assert(!(tags contains "eslint"))
        assert(!(tags contains "eslint-plugin-promise"))
      }

//      "return bad grades when code is bad" in { ??? }
//      "return good grades when code is good" in { ??? }
//      "return code coverage grade" in { ??? }
//      "return all supported grade types" in { ??? }
//      "ignore users with too low total grade" in { ??? }
//      "detect services used" in {
//        assert(
//          fixture.servicesOf("alopatindev", "qdevicemonitor") === Seq("travis-ci.org", "appveyor.com")
//          fixture.servicesOf("alopatindev", "find-telegram-bot") === Seq(
//            "travis-ci.org",
//            "codecov.io",
//            "codeclimate.com",
//            "semaphoreci.com",
//            "bithound.io",
//            "versioneye.com",
//            "david-dm.org",
//            "dependencyci.com",
//            "snyk.io",
//            "npmjs.com"
//          ))
//      }

    }

  }

  override def withFixture(test: OneArgTest): Outcome = {
    implicit val sparkContext = sc
    implicit val sparkSession = SparkSession.builder
      .config(sparkContext.getConf)
      .getOrCreate()

    import sparkSession.implicits._

    val appConfig = ConfigFactory.load("GraderFixture.conf")
    val warningsToGradeCategory = sparkContext
      .parallelize(
        Seq(
          ("eqeqeq", "JavaScript", "Robust")
        ))
      .toDF("warning", "tag", "gradeCategory")
      .as[WarningToGradeCategory]

    val weightedTechnologies = Seq("ESLint")
    val grader = new Grader(appConfig, warningsToGradeCategory, weightedTechnologies)
    val theFixture = FixtureParam(grader)
    try {
      withFixture(test.toNoArgTest(theFixture))
    } finally {}
  }

  case class FixtureParam(val grader: Grader) {

    val branch = "master"

    def runAnalyzerScript(login: String, // scalastyle:ignore
                          repoName: String,
                          language: String,
                          withCleanup: Boolean = true)
      : (Dataset[AnalyzerScriptResult], (String) => Boolean, (String, String) => Boolean, String) = {
      val repoId = UUID.randomUUID().toString
      val archiveURL = s"https://github.com/${login}/${repoName}/archive/${branch}.tar.gz"
      val input: Seq[String] = Seq(s"${repoId};${archiveURL};${language}")

      val results = grader.runAnalyzerScript(input, withCleanup)

      val assetsDir = grader.appConfig.getString("app.assetsDir")

      def file(path: String): File =
        new File(s"${assetsDir}/data/${repoId}/${repoName}-${branch}${path}")

      def pathExists(path: String): Boolean =
        file(path).exists()

      def fileContainsText(path: String, pattern: String): Boolean =
        Source.fromFile(file(path)).mkString contains pattern

      (results, pathExists, fileContainsText, repoId)
    }

    def processAnalyzerScriptResults(
        login: String,
        repoName: String,
        language: String): (Iterable[GraderResult], (String) => Boolean, (String, String) => Boolean, String) = {
      val (outputMessages, pathExists, fileContainsText, repoId) = runAnalyzerScript(login, repoName, language)
      val results = grader.processAnalyzerScriptResults(outputMessages)
      (results, pathExists, fileContainsText, repoId)
    }

  }

}
