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
