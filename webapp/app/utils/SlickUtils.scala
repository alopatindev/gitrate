package utils

import com.github.tminglei.slickpg.utils.PlainSQLUtils
import play.api.db.slick.DatabaseConfigProvider
import scala.concurrent.Future
import slick.dbio.{DBIOAction, Effect, NoStream}
import slick.jdbc.JdbcProfile

trait SlickUtils {

  val dbConfigProvider: DatabaseConfigProvider

  type SQLQuery[T] = DBIOAction[T, NoStream, Nothing]
  type SQLTransaction[T] = DBIOAction[T, NoStream, Effect with Effect.Transactional]

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._

  implicit val textArray = PlainSQLUtils.mkArraySetParameter[String]("text")

  def runQuery[T](query: SQLQuery[T]): Future[T] = db.run(query)

}
