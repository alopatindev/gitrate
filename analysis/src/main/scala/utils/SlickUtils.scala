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
