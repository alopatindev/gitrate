package analysis

import controllers.GraderController.{GradeCategory, WarningToGradeCategory}
import testing.TestUtils

import com.holdenkarau.spark.testing.DataFrameSuiteBase
import com.typesafe.config.ConfigFactory
import java.io.File
import java.net.URL
import java.nio.file.{Path, Paths}
import java.util.UUID
import org.apache.commons.io.FileUtils
import org.apache.spark.SparkContext
import org.apache.spark.sql.{Dataset, SparkSession}
import org.scalatest.Matchers._
import org.scalatest.tagobjects.Slow
import org.scalatest.{Outcome, fixture}
import scala.io.Source

class GraderSuite extends fixture.WordSpec with DataFrameSuiteBase with TestUtils {

  "runAnalyzerScript" should {

    "ignore repository with invalid files or directories" taggedAs Slow in { fixture =>
      val login = "himanshuchandra"
      val repoName = "react.js-codes"
      val languages = Set("JavaScript")
      val (results, _, _, repoId) = fixture.runAnalyzerScript(login, repoName, languages)
      val invalidResults = results.collect().filter(_.idBase64 == repoId)
      assert(invalidResults.isEmpty)
    }

    "compute lines of code" taggedAs Slow in { fixture =>
      val login = "pkrumins"
      val repoName = "node-png"
      val languages = Set("C", "C++", "JavaScript")
      val (results, _, _, _) = fixture.runAnalyzerScript(login, repoName, languages, branch = "3.0.0")

      val linesOfCode: Map[String, Int] = results
        .collect()
        .filter(result => (languages contains result.language) && result.messageType == "lines_of_code")
        .map(result => result.language -> result.message.toInt)
        .toMap

      val expectedLinesOfCode = Map("C" -> 91, "C++" -> 961, "JavaScript" -> 140)
      assert(linesOfCode === expectedLinesOfCode)
    }

    "remove temporary files when done" taggedAs Slow in { fixture =>
      val login = "alopatindev"
      val repoName = "find-telegram-bot"
      val languages = Set("JavaScript")

      val (results, pathExists, _, _) = fixture.runAnalyzerScript(login, repoName, languages)
      val idBase64 = results.collect().head.idBase64
      val archiveName = s"$idBase64.tar.gz"

      val directoryExists = pathExists("/")
      val archiveExists = pathExists(s"/../../$archiveName")

      assert(!directoryExists && !archiveExists)
    }

    "run with time limit and remove temporary files when done" taggedAs Slow in { fixture =>
      val login = "qt"
      val repoName = "qtbase"
      val languages = Set("C++")
      val (results, pathExists, _, repoId) = fixture.runAnalyzerScript(login, repoName, languages, branch = "5.9")
      val limit = fixture.grader.appConfig.getDuration("grader.maxExternalScriptDuration")

      shouldRunAtMost(limit) {
        val _ = results.collect()
      }

      val archiveName = s"$repoId.tar.gz"
      val directoryExists = pathExists("/")
      val archiveExists = pathExists(s"/../../$archiveName")

      val _ = assert(!directoryExists && !archiveExists)
    }

    "ignore too big repository" taggedAs Slow in { fixture =>
      val login = "qt"
      val repoName = "qtbase"
      val languages = Set("C++")
      val (results, _, _, repoId) = fixture.runAnalyzerScript(login, repoName, languages, branch = "5.3")
      val messages = results.collect().filter(_.idBase64 == repoId)
      assert(messages.isEmpty)
    }

    "detect automation tools" taggedAs Slow in { fixture =>
      val login = "alopatindev"
      val repoName = "find-telegram-bot"
      val languages = Set("JavaScript")

      val (results, _, _, _) = fixture.runAnalyzerScript(login, repoName, languages)
      val messages: Seq[String] = results
        .collect()
        .filter(message => message.language == "all_languages" && message.messageType == "automation_tool")
        .map(_.message)
      assert(messages contains "travis")
      assert(messages contains "circleci")
      assert(messages contains "docker")
      assert(messages contains "codeclimate")
      assert(messages contains "codeship")
      assert(messages contains "semaphoreci")
      assert(messages contains "david-dm")
      assert(messages contains "dependencyci")
      assert(messages contains "bithound")
      assert(messages contains "snyk")
      assert(messages contains "versioneye")
      assert(messages contains "codecov")
    }

// "ignore repositories with generated/downloaded files (*.o, *.so, node_modules, etc.)" taggedAs Slow in { fixture =>
//     ???
// }
//
// "ignore repository if script fails" taggedAs Slow in { fixture =>
//   ???
// }

  }

  "perform JavaScript analysis" should {

    "detect dependencies" taggedAs Slow in { fixture =>
      val login = "alopatindev"
      val repoName = "find-telegram-bot"
      val languages = Set("JavaScript")
      val (results, _, _, _) = fixture.runAnalyzerScript(login, repoName, languages)
      val dependencies: Seq[String] = results
        .collect()
        .filter(result => (languages contains result.language) && result.messageType == "dependence")
        .map(_.message)
      assert(dependencies contains "phantom")
      assert(dependencies contains "eslint")
      assert(dependencies contains "eslint-plugin-promise")
      assert(!(dependencies contains "PhantomJS"))
      assert(dependencies.toSet.size === dependencies.length)
    }

//      "ignore scoped dependencies" taggedAs Slow in { fixture =>
//        ???
//      }

    "detect Node.js dependence" taggedAs Slow in { fixture =>
      def hasDependence(login: String, repoName: String): Boolean = {
        val languages = Set("JavaScript")
        val (results, _, _, _) = fixture.runAnalyzerScript(login, repoName, languages)
        val dependencies: Set[String] = results
          .collect()
          .filter(result => (languages contains result.language) && result.messageType == "dependence")
          .map(_.message)
          .toSet
        dependencies contains "Node.js"
      }

      assert(hasDependence(login = "alopatindev", repoName = "find-telegram-bot"))
      assert(hasDependence(login = "gorillamania", repoName = "package.json-validator"))
      assert(!hasDependence(login = "jquery", repoName = "jquery.com"))
    }

    "handle absent dependencies" taggedAs Slow in { fixture =>
      val login = "Marak"
      val repoName = "hellonode"
      val languages = Set("JavaScript")
      val (results, _, _, _) = fixture.runAnalyzerScript(login, repoName, languages)
      val dependencies: Set[String] = results
        .collect()
        .filter(result => (languages contains result.language) && result.messageType == "dependence")
        .map(_.message)
        .toSet
      assert(dependencies.isEmpty)
    }

    "ignore repository without package.json or bower.json" taggedAs Slow in { fixture =>
      val login = "moisseev"
      val repoName = "BackupPC_Timeline"
      val languages = Set("JavaScript")
      val (results, _, _, _) = fixture.runAnalyzerScript(login, repoName, languages)
      val messages = results
        .collect()
        .filter(result => languages contains result.language)
      assert(messages.isEmpty)
    }

    "ignore repository when url from package.json / bower.json doesn't match with git url" taggedAs Slow in { fixture =>
      val login = "mquandalle"
      val repoName = "react-native-vector-icons"
      val languages = Set("JavaScript")
      val (results, _, _, _) = fixture.runAnalyzerScript(login, repoName, languages)
      val messages = results
        .collect()
        .filter(result => languages contains result.language)
      assert(messages.isEmpty)
    }

    "remove package.json, package-lock.json, bower.json, *.eslint*, yarn.lock, .gitignore" taggedAs Slow in { fixture =>
      val login = "alopatindev"
      val repoName = "find-telegram-bot"
      val languages = Set("JavaScript")
      val (results, pathExists, _, _) = fixture.runAnalyzerScript(login, repoName, languages, withCleanup = false)
      val _ = results.collect()
      assert(!pathExists("/package.json"))
      assert(!pathExists("/package-lock.json"))
      assert(!pathExists("/bower.json"))
      assert(!pathExists("/.eslintrc.yml"))
      assert(!pathExists("/yarn.lock"))
      assert(!pathExists("/.gitignore"))
    }

    "remove minified files" taggedAs Slow in { fixture =>
      val login = "Masth0"
      val repoName = "TextRandom"
      val languages = Set("JavaScript")
      val (results, pathExists, _, _) = fixture.runAnalyzerScript(login, repoName, languages, withCleanup = false)
      val _ = results.collect()
      assert(!pathExists("/dist/TextRandom.min.js"))
    }

    "remove third-party libraries" taggedAs Slow in { fixture =>
      val login = "oblador"
      val repoName = "react-native-vector-icons"
      val languages = Set("JavaScript")
      val (results, pathExists, _, _) = fixture.runAnalyzerScript(login, repoName, languages, withCleanup = false)
      val _ = results.collect()
      assert(pathExists("/index.js"))
      assert(pathExists("/Examples"))
      assert(!pathExists("/Examples/IconExplorer/IconList.js"))
      assert(!pathExists("/Examples/IconExplorer"))
    }

    "remove comments" taggedAs Slow in { fixture =>
      val login = "Masth0"
      val repoName = "TextRandom"
      val languages = Set("JavaScript")
      val (results, _, fileContainsText, _) =
        fixture.runAnalyzerScript(login, repoName, languages, withCleanup = false)
      val _ = results.collect()
      assert(!fileContainsText("/src/TextRandom.js", "Create or remove span"))
      assert(!fileContainsText("/src/TextRandom.js", "Animate random characters"))
    }

  }

  "perform C and C++ analysis" should {

    "compute lines of code" taggedAs Slow in { fixture =>
      val login = "alopatindev"
      val languages = Set("C", "C++")
      val repoName = "qdevicemonitor"
      val (results, _, _, _) = fixture.runAnalyzerScript(login, repoName, languages)
      val linesOfCode: Seq[Int] = results
        .collect()
        .filter(result => (languages contains result.language) && result.messageType == "lines_of_code")
        .map(_.message.toInt)
        .take(2)
      assert(linesOfCode.length === 2 && linesOfCode.forall(_ > 0))
    }

    "detect warnings" taggedAs Slow in { fixture =>
      val login = "alopatindev"
      val languages = Set("C", "C++")
      val repoName = "qdevicemonitor"
      val (results, _, _, _) = fixture.runAnalyzerScript(login, repoName, languages)
      val warnings: Seq[AnalyzerScriptResult] = results
        .collect()
        .filter(result => (languages contains result.language) && result.messageType == "warning")
      assert(warnings.nonEmpty)
    }

    "detect dependencies" taggedAs Slow in { fixture =>
      val login = "alopatindev"
      val languages = Set("C", "C++")
      val repoName = "qdevicemonitor"
      val (results, _, _, _) = fixture.runAnalyzerScript(login, repoName, languages)
      val dependencies: Set[String] = results
        .collect()
        .filter(result => (languages contains result.language) && result.messageType == "dependence")
        .map(_.message)
        .toSet
      assert(dependencies contains "libc")
      assert(dependencies contains "STL")
      assert(dependencies contains "Qt")
    }

  }

  "processAnalyzerScriptResults" should {

    "compute total lines of code" taggedAs Slow in { fixture =>
      val login = "pkrumins"
      val repoName = "node-png"
      val languages = Set("C", "C++", "JavaScript")
      val (results: Iterable[GradedRepository], _, _, _) =
        fixture.processAnalyzerScriptResults(login, repoName, languages, branch = "3.0.0")
      val expected = 1192L
      assert(results.head.linesOfCode === expected)
    }

    "detect parent dependencies" taggedAs Slow in { fixture =>
      val login = "alopatindev"
      val repoName = "find-telegram-bot"
      val language = "JavaScript"
      val (results: Iterable[GradedRepository], _, _, _) =
        fixture.processAnalyzerScriptResults(login, repoName, Set(language))
      val technologies: Seq[String] = results.head.languageToTechnologies(language)
      assert(technologies contains "eslint")
      assert(technologies contains "eslint-plugin-promise")
    }

    "return all supported grade types" taggedAs Slow in { fixture =>
      val login = "Masth0"
      val repoName = "TextRandom"
      val languages = Set("JavaScript")
      val (results: Iterable[GradedRepository], _, _, _) =
        fixture.processAnalyzerScriptResults(login, repoName, languages)

      val gradeCategoriesSeq = results.head.grades.map(_.gradeCategory)
      val gradeCategories = gradeCategoriesSeq.toSet
      assert(gradeCategoriesSeq.length === gradeCategories.size)

      val expectedGradeCategories = Set("Maintainable", "Testable", "Robust", "Secure", "Automated", "Performant")
      assert(gradeCategories === expectedGradeCategories)
    }

    "return good grades when code is good" taggedAs Slow in { fixture =>
      val login = "Masth0"
      val repoName = "TextRandom"
      val languages = Set("JavaScript")
      val (results: Iterable[GradedRepository], _, _, _) =
        fixture.processAnalyzerScriptResults(login, repoName, languages)

      def grade(category: String): Double = results.head.grades.find(_.gradeCategory == category).get.value
      val tolerance = 0.00001
      assert(grade("Robust") === 0.96891 +- tolerance)
      assert(grade("Automated") === 0.0 +- tolerance)
      assert(grade("Performant") === 1.0 +- tolerance)
      assert(grade("Secure") === 1.0 +- tolerance)
      assert(grade("Maintainable") === 1.0 +- tolerance)
      assert(grade("Testable") === 0.0 +- tolerance)
    }

    "return automated grade based on detected automation tools" taggedAs Slow in { fixture =>
      val login = "alopatindev"
      val repoName = "find-telegram-bot"
      val language = "JavaScript"
      val (results: Iterable[GradedRepository], _, _, _) =
        fixture.processAnalyzerScriptResults(login, repoName, Set(language))
      val grade = results.head.grades.find(_.gradeCategory == "Automated").head
      assert(grade.value === 1.0 +- 0.1)
    }

    "return bad automated grade if no automation tools detected" taggedAs Slow in { fixture =>
      val login = "Masth0"
      val repoName = "TextRandom"
      val language = "JavaScript"
      val (results: Iterable[GradedRepository], _, _, _) =
        fixture.processAnalyzerScriptResults(login, repoName, Set(language))
      val grade = results.head.grades.find(_.gradeCategory == "Automated").head
      assert(grade.value === 0.0 +- 0.1)
    }

    "return testable grade based on test directories detection" taggedAs Slow in { fixture =>
      val login = "alopatindev"
      val repoName = "find-telegram-bot"
      val language = "JavaScript"
      val (results: Iterable[GradedRepository], _, _, _) =
        fixture.processAnalyzerScriptResults(login, repoName, Set(language))
      val grade = results.head.grades.find(_.gradeCategory == "Testable").head
      assert(grade.value === 1.0 +- 0.1)
    }

    "return bad testable grade if no test directory detected" taggedAs Slow in { fixture =>
      val login = "Masth0"
      val repoName = "TextRandom"
      val language = "JavaScript"
      val (results: Iterable[GradedRepository], _, _, _) =
        fixture.processAnalyzerScriptResults(login, repoName, Set(language))
      val grade = results.head.grades.find(_.gradeCategory == "Testable").head
      assert(grade.value === 0.0 +- 0.1)
    }

    // "return testable based on coveralls" taggedAs Slow in { ??? }
    // "return testable based on scrutinizer-ci" taggedAs Slow in { ??? }
    // "return testable based on codecov" taggedAs Slow in { ??? }
    // "return testable based on codeclimate" taggedAs Slow in { ??? }
    // "return testable based on codacy" taggedAs Slow in { ??? }

  }

  override def withFixture(test: OneArgTest): Outcome = {
    implicit val sparkContext: SparkContext = sc
    implicit val sparkSession: SparkSession = SparkSession.builder
      .config(sparkContext.getConf)
      .getOrCreate()

    import sparkSession.implicits._

    val appConfig = ConfigFactory.load("GraderFixture.conf")
    val warningsToGradeCategory = sparkContext
      .parallelize(
        Seq(
          ("eqeqeq", "JavaScript", "Robust")
        ))
      .toDF("warning", "language", "gradeCategory")
      .as[WarningToGradeCategory]

    val gradeCategories = sparkContext
      .parallelize(
        Seq(
          "Maintainable",
          "Testable",
          "Robust",
          "Secure",
          "Automated",
          "Performant"
        ))
      .toDF("gradeCategory")
      .as[GradeCategory]

    val grader = new Grader(appConfig, warningsToGradeCategory, gradeCategories)
    val dataDir: Path = Paths.get(appConfig.getString("app.scriptsDir"), "data")
    val theFixture = FixtureParam(grader, dataDir)
    try {
      withFixture(test.toNoArgTest(theFixture))
    } finally {
      val dir = new File(dataDir.toUri)
      FileUtils.deleteDirectory(dir)
      val _ = dir.mkdir()
    }
  }

  case class FixtureParam(grader: Grader, dataDir: Path) {

    def runAnalyzerScript(login: String, // scalastyle:ignore
                          repoName: String,
                          languages: Set[String],
                          branch: String = defaultBranch,
                          withCleanup: Boolean = true)
      : (Dataset[AnalyzerScriptResult], (String) => Boolean, (String, String) => Boolean, String) = {
      val repoId = UUID.randomUUID().toString
      val archiveURL = new URL(s"https://github.com/$login/$repoName/archive/$branch.tar.gz")
      val input: Seq[String] = Seq(grader.ScriptInput(repoId, repoName, login, archiveURL, languages).toString)

      val results = grader.runAnalyzerScript(input, withCleanup)

      def file(path: String): File =
        dataDir
          .resolve(Paths.get(repoId, s"$repoName-$branch", path))
          .normalize
          .toFile

      def pathExists(path: String): Boolean = file(path).exists()

      def fileContainsText(path: String, pattern: String): Boolean =
        Source.fromFile(file(path)).mkString contains pattern

      (results, pathExists, fileContainsText, repoId)
    }

    def processAnalyzerScriptResults(login: String, // scalastyle:ignore
                                     repoName: String,
                                     languages: Set[String],
                                     branch: String = defaultBranch)
      : (Iterable[GradedRepository], (String) => Boolean, (String, String) => Boolean, String) = {
      val (outputMessages, pathExists, fileContainsText, repoId) =
        runAnalyzerScript(login, repoName, languages, branch = branch)
      val results = grader.processAnalyzerScriptResults(outputMessages)
      (results, pathExists, fileContainsText, repoId)
    }

    private val defaultBranch: String = "master"

  }

}
