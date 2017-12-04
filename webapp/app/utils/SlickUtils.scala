package utils

import com.github.tminglei.slickpg._
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

  trait PostgresDriverPg extends ExPostgresProfile with PgDateSupport with PgDate2Support with PgArraySupport {

    override val api = MyAPI
    object MyAPI extends API with ArrayImplicits with DateTimeImplicits with SimpleArrayPlainImplicits {

      implicit val strListTypeMapper = new SimpleArrayJdbcType[String]("text").to(_.toList)

    }

  }

  object PostgresDriverPgExt extends PostgresDriverPg

  def runQuery[T](query: SQLQuery[T]): Future[T] = db.run(query)

}
