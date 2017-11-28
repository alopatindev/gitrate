package testing

import org.scalatest.TestData
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import org.scalatestplus.play.PlaySpec
import play.api.Application
import play.api.db.DBApi
import play.api.db.evolutions.Evolutions
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.guice.GuiceApplicationBuilder
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.language.implicitConversions
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlAction

trait PostgresTestUtils {

  this: PlaySpec with GuiceOneAppPerTest =>

  def initialData: SqlAction[Int, NoStream, Effect] = sqlu""

  implicit override def newAppForTest(testData: TestData): Application = {
    val slickDatabase = "default"
    val user = "gitrate_test"
    val database = user
    val databaseKeyPrefix = s"slick.dbs.$slickDatabase.db.properties"
    val app = new GuiceApplicationBuilder()
      .configure(Map(s"$databaseKeyPrefix.user" -> user, s"$databaseKeyPrefix.url" -> s"jdbc:postgresql:$database"))
      .build()

    val databaseApi = app.injector.instanceOf[DBApi]
    val defaultDb = databaseApi.database(slickDatabase)
    Evolutions.cleanupEvolutions(defaultDb)
    Evolutions.applyEvolutions(defaultDb)

    val dbConfigProvider = app.injector.instanceOf[DatabaseConfigProvider]
    val dbConfig = dbConfigProvider.get[JdbcProfile]
    import dbConfig._
    import profile.api._

    val result = db.run(initialData.transactionally)
    Await.result(result, Duration.Inf)

    app
  }

}
