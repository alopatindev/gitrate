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

import com.github.tminglei.slickpg.{ExPostgresProfile, PgArraySupport}
import play.api.db.slick.DatabaseConfigProvider
import scala.concurrent.Future
import slick.dbio.{DBIOAction, Effect, NoStream}
import slick.jdbc.JdbcProfile

trait SlickUtils {

  val dbConfigProvider: DatabaseConfigProvider

  type SQLQuery[T] = DBIOAction[T, NoStream, Nothing]
  type SQLTransaction[T] = DBIOAction[T, NoStream, Effect with Effect.Transactional]

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig.db

  trait PostgresDriverPg extends ExPostgresProfile with PgArraySupport {

    override val api = MyAPI
    object MyAPI extends API with ArrayImplicits with SimpleArrayPlainImplicits {

      implicit val strListTypeMapper = new SimpleArrayJdbcType[String]("text").to(_.toList)

    }

  }

  object PostgresDriverPgExt extends PostgresDriverPg

  def runQuery[T](query: SQLQuery[T]): Future[T] = db.run(query)

}
