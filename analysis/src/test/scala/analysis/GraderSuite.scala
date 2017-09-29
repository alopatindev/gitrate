package gitrate.analysis

import gitrate.utils.TestUtils

import com.holdenkarau.spark.testing.DataFrameSuiteBase

import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.SparkSession
import org.scalatest.{fixture, Outcome}

class GraderSuite extends fixture.WordSpec with DataFrameSuiteBase with TestUtils {

  import com.typesafe.config.ConfigFactory

  for (i <- Seq("org.apache.spark", "org.apache.hadoop.hive", "Grader")) {
    Logger.getLogger(i).setLevel(Level.ERROR)
  }

  "Grader" can {

//  "common analysis" should {
//      "ignore repositories that contain files with bad filenames (or directory names)" { ??? }
//      "download repo" in {
//        fixture.downloadRepo("alopatindev", "find-telegram-bot")
//        eventually {
//          assert(fixture.fileExists("/tmp/gitrate-analyzer/alopatindev/find-telegram-bot/.gitignore"))
//        }
//      }
//      "compute lines of code" in { ??? }
//      "remove comments" in { ??? }
//      "rename dependencies and ignore aliases" in {
//        assert(fixture.dependenciesOf("alopatindev", "find-telegram-bot") contains "PhantomJS")
//        assert(!(fixture.dependenciesOf("alopatindev", "find-telegram-bot") contains "phantomjs"))
//      }
//      "cleanup temporary files when done" in {
//        fixture.cleanup("alopatindev", "find-telegram-bot")
//        eventually {
//          assert(!fixture.fileExists("/tmp/gitrate-analyzer/alopatindev/find-telegram-bot/.gitignore"))
//        }
//      }
//    }
//
//    "JavaScript analysis" should {
//      "remove node_modules, package.json, bower.json, *.eslint*, yarn.lock" in { ??? }
//      "remove third-party libraries" in { ??? }
//      "detect dependencies" in {
//        fixture.downloadRepo("alopatindev", "find-telegram-bot")
//        eventually {
//          assert(fixture.rawDependenciesOf("alopatindev", "find-telegram-bot") === Seq("phantom", "telegraf", "winston", "bithound", "codecov", "eslint", "eslint-plugin-better", "eslint-plugin-mocha", "eslint-plugin-private-props", "eslint-plugin-promise", "istanbul", "mocha", "mocha-logger", "nodemon"))
//        }
//      }
//    }
//
//    // TODO: separate module?
//    "static analysis" should {
//      "apply analysis of supported languages used in the repo" in { ??? }
//      "run on the same machine and container as wget" in { ??? }
//      "return bad grades when code is bad" in { ??? }
//      "return good grades when code is good" in { ??? }
//      //"return code coverage grade" in { ??? }
//      "return all supported grade types" in { ??? }
//      //"ignore code that can't compile" in { ??? } // it can fail because of dependencies we don't have
//      "ignore users with too low total grade" in { ??? }
//    }
//
//  "fetch additional info" should {
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
//    }

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
      .toDF("warning", "tag", "grade_category")

    val grader = new Grader(appConfig, warningsToGradeCategory)
    val theFixture = FixtureParam(grader)
    try {
      withFixture(test.toNoArgTest(theFixture))
    } finally {}
  }

  case class FixtureParam(val grader: Grader)

}
