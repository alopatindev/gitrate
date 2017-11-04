package testing

import org.scalatest.{Outcome, fixture}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.io.Source
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlAction

trait PostgresTestUtils extends fixture.WordSpec {

  def initialData: SqlAction[Int, NoStream, Effect] = sqlu""

  case class FixtureParam()

  override def withFixture(test: OneArgTest): Outcome = {
    val user = "gitrate_test"
    val database = user
    val url = s"jdbc:postgresql:$database"
    val driver = "org.postgresql.Driver"
    val db: Database = Database.forURL(url = url, user = user, driver = driver)

    val dropDataSQL = sqlu"DROP OWNED BY #$user"
    val result = db.run(DBIO.seq(dropDataSQL, loadSQL("schema.sql"), loadSQL("data.sql"), initialData).transactionally)
    Await.result(result, Duration.Inf)

    val theFixture = FixtureParam()
    try {
      withFixture(test.toNoArgTest(theFixture))
    } finally {
      val result = db.run(dropDataSQL.transactionally)
      Await.result(result, Duration.Inf)

      db.close()
    }
  }

  private def loadSQL(script: String) = {
    val text = Source.fromFile(sqlScriptsDir + script).mkString
    sqlu"#$text"
  }

  private val sqlScriptsDir = "analysis/conf/"

}
