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

package utils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlStreamingAction

trait SlickUtils extends LogUtils {

  type SQLTransaction[T] = DBIOAction[T, NoStream, Effect with Effect.Transactional]
  type SQLQuery[Container, T] = SqlStreamingAction[Container, T, Effect]

  def runQuery[T](query: SQLTransaction[T]): Future[T] = {
    val db = createDatabase()
    val result = db.run(query).logErrors()
    result.onComplete(_ => db.close())
    result
  }

  def runQuery[Container, T](query: SQLQuery[Container, T]): Future[Container] = {
    val db = createDatabase()
    val result = db.run(query).logErrors()
    result.onComplete(_ => db.close())
    result
  }

  private def createDatabase(): Database = Database.forConfig("db.postgresql")

}
