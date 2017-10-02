package gitrate.analysis

import gitrate.utils.TestUtils

import com.holdenkarau.spark.testing.DataFrameSuiteBase

import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.{Dataset, SparkSession}
import org.scalatest.{fixture, Outcome}

class GraderSuite extends fixture.WordSpec with DataFrameSuiteBase with TestUtils {

  import com.typesafe.config.ConfigFactory
  import java.io.File

  for (i <- Seq("org.apache.spark", "org.apache.hadoop.hive", "Grader")) {
    Logger.getLogger(i).setLevel(Level.ERROR)
  }

  "Grader" can {

    "runAnalyzerScript" should {

      "ignore repositories that contain files or directories with bad names" in { fixture =>
        val login = "himanshuchandra"
        val repoId = "test_0"
        val repoName = "react.js-codes"
        val language = "JavaScript"
        val results: Seq[AnalyzerScriptResult] = fixture
          .runAnalyzerScript(login, repoId, repoName, language)
          .collect()
        val invalidResults = results.filter(_.idBase64 == repoId)
        assert(invalidResults.isEmpty)
      }

      "remove temporary files when done" in { fixture =>
        val login = "alopatindev"
        val repoId = "test_1"
        val repoName = "find-telegram-bot"
        val language = "JavaScript"
        val _ = fixture.runAnalyzerScript(login, repoId, repoName, language).collect()
        val assetsDir = fixture.grader.appConfig.getString("app.assetsDir")
        val directoryExists = new File(s"${assetsDir}/data/${repoId}").exists()
        assert(!directoryExists)
      }

      "process multiple languages" in { fixture =>
        ???
      }

    }

    "JavaScript analysis" should {

      "compute lines of code" in { fixture =>
        val login = "alopatindev"
        val repoId = "test_2"
        val repoName = "find-telegram-bot"
        val language = "JavaScript"
        val results: Seq[AnalyzerScriptResult] = fixture
          .runAnalyzerScript(login, repoId, repoName, language)
          .collect()
        val linesOfCode: Option[Int] = results
          .filter(result => result.language == language && result.messageType == "lines_of_code")
          .headOption
          .map(_.message.toInt)
        assert(linesOfCode.isDefined && linesOfCode.get > 0)
      }

      "detect dependencies" in { fixture =>
        val login = "alopatindev"
        val repoId = "test_3"
        val repoName = "find-telegram-bot"
        val language = "JavaScript"
        val results: Seq[AnalyzerScriptResult] = fixture
          .runAnalyzerScript(login, repoId, repoName, language)
          .collect()
        val dependencies: Set[String] = results
          .filter(result => result.language == language && result.messageType == "dependence")
          .map(_.message)
          .toSet
        assert(dependencies contains "phantom")
        assert(dependencies contains "eslint")
        assert(dependencies contains "eslint-plugin-promise")
        assert(!(dependencies contains "PhantomJS"))
      }

//      "remove node_modules, package.json, bower.json, *.eslint*, yarn.lock" in { fixture => ??? }
//      "remove minified files" in { fixture => ??? } // https://github.com/Masth0/TextRandom
//      "remove third-party libraries" in { fixture => ??? }
//      "remove comments" in { fixture => ??? }
    }

    "processAnalyzerScriptResults" should {

      "ignore dependence of dependence" in { fixture =>
        val login = "alopatindev"
        val repoId = "test_4"
        val repoName = "find-telegram-bot"
        val language = "JavaScript"
        val results: Iterable[GraderResult] = fixture.processAnalyzerScriptResults(login, repoId, repoName, language)
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

    def runAnalyzerScript(login: String,
                          repoId: String,
                          repoName: String,
                          language: String): Dataset[AnalyzerScriptResult] = {
      val branch = "master"
      val archiveURL = s"https://github.com/${login}/${repoName}/archive/${branch}.tar.gz"
      val input: Seq[String] = Seq(s"${repoId};${archiveURL};${language}")
      grader.runAnalyzerScript(input)
    }

    def processAnalyzerScriptResults(login: String,
                                     repoId: String,
                                     repoName: String,
                                     language: String): Iterable[GraderResult] = {
      val outputMessages = runAnalyzerScript(login, repoId, repoName, language)
      grader.processAnalyzerScriptResults(outputMessages)
    }

  }

}
