// Copyright 2017 Alexander Lopatin
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package testing

import utils.AppConfig

import java.nio.file.Paths
import org.scalatest.{Outcome, fixture}
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.io.Source
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlAction

trait PostgresTestUtils extends fixture.WordSpec with AppConfig {

  def initialData: SqlAction[Int, NoStream, Effect] = sqlu""

  case class FixtureParam()

  override def withFixture(test: OneArgTest): Outcome = {
    val user = postgresqlConfig.getString("user")
    val url = postgresqlConfig.getString("url")
    val driver = postgresqlConfig.getString("driver")
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
    val path = Paths.get("conf", script).toUri
    val text = Source.fromFile(path).mkString
    sqlu"#$text"
  }

}
