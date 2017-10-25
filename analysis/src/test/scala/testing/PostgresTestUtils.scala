package testing

import org.scalatest.{Outcome, fixture}
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlAction

trait PostgresTestUtils extends fixture.WordSpec {

  def schema: SqlAction[Int, NoStream, Effect]
  def initialData: SqlAction[Int, NoStream, Effect]

  case class FixtureParam()

  override def withFixture(test: OneArgTest): Outcome = {
    val user = "gitrate_test"
    val database = user
    val url = s"jdbc:postgresql:$database"
    val driver = "org.postgresql.Driver"

    val db: Database = Database.forURL(url = url, user = user, driver = driver)
    val dropData: SqlAction[Int, NoStream, Effect] = sqlu"DROP OWNED BY #$user"
    val result = db.run(DBIO.seq(dropData, schema, initialData).transactionally)
    Await.result(result, Duration.Inf)

    val theFixture = FixtureParam()
    try {
      withFixture(test.toNoArgTest(theFixture))
    } finally {
      db.close()
    }
  }

}
