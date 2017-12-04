package controllers

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents, Request}
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class HomeController @Inject()(cc: ControllerComponents,
                               configuration: Configuration,
                               val dbConfigProvider: DatabaseConfigProvider,
                               queryParser: QueryParser,
                               searcher: Searcher,
                               suggester: Suggester)
    extends AbstractController(cc) {

  def index(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }

  def search(query: String, page: Int): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    searcher
      .search(query, page)
      .map(view => Ok(view))
  }

  def suggest(query: String): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    suggester
      .suggest(query)
      .map(view => Ok(view))
  }

}
