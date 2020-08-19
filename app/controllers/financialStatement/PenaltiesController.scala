/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers.financialStatement

import connectors.FinancialStatementConnector
import connectors.cache.FinancialInfoCacheConnector
import controllers.actions._
import javax.inject.Inject
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.{PenaltiesService, SchemeService}
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import config.Constants._
import scala.concurrent.{ExecutionContext, Future}

class PenaltiesController @Inject()(identify: IdentifierAction,
                                    override val messagesApi: MessagesApi,
                                    val controllerComponents: MessagesControllerComponents,
                                    fsConnector: FinancialStatementConnector,
                                    fiCacheConnector: FinancialInfoCacheConnector,
                                    penaltiesService: PenaltiesService,
                                    schemeService: SchemeService,
                                    renderer: Renderer
                                   )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def onPageLoad(year: String, identifier: String): Action[AnyContent] = identify.async {
    implicit request =>

      def viewModel(pstr: String, schemeAssociated: Boolean, tables: Seq[JsObject], args: String*): JsObject =
        Json.obj(
          "year" -> year,
          "pstr" -> pstr,
          "schemeAssociated" -> schemeAssociated,
          "tables" -> tables,
          "schemeName" -> args
        )

      fsConnector.getPsaFS(request.psaId.id).flatMap {
        psaFS =>
          if (identifier.matches(srnRegex)) {
            schemeService.retrieveSchemeDetails(request.psaId.id, identifier) flatMap {
              schemeDetails =>
                val filteredPsaFS =
                  psaFS.filter(_.pstr == schemeDetails.pstr)

                println(s"\n\nPenaltiesController filteredPsaFS Size:\t${filteredPsaFS.size}\n\n")

                val penaltyTables: Seq[JsObject] =
                  penaltiesService.getPsaFsJson(filteredPsaFS, identifier, year.toInt).filter(_ != Json.obj())

                println(s"\n\nPenaltiesController penaltyTables Size:\t${penaltyTables.size}\n\n")

                val json = viewModel(
                  pstr = schemeDetails.pstr,
                  schemeAssociated = true,
                  tables = penaltyTables,
                  args = schemeDetails.schemeName
                )

                renderer.render(template = "financialStatement/penalties.njk", json).map(Ok(_))
            }
          } else {
            fiCacheConnector.fetch flatMap {
              case Some(jsValue) =>
                val pstrs: Seq[String] =
                  (jsValue \ "pstrs").as[Seq[String]]

                val filteredPsaFS =
                  psaFS.filter(_.pstr == pstrs(identifier.toInt))

                val penaltyTables: Seq[JsObject] =
                  penaltiesService.getPsaFsJson(filteredPsaFS, identifier, year.toInt).filter(_ != Json.obj())

                val json = viewModel(
                  pstr = pstrs(identifier.toInt),
                  schemeAssociated = false,
                  tables = penaltyTables
                )

                renderer.render(template = "financialStatement/penalties.njk", json).map(Ok(_))
              case _ =>
                Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
            }
          }
      }
  }
}
