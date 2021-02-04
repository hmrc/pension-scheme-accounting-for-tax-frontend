/*
 * Copyright 2021 HM Revenue & Customs
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

package controllers.financialStatement.penalties

import config.Constants._
import connectors.FinancialStatementConnector
import connectors.cache.FinancialInfoCacheConnector
import controllers.actions._
import models.financialStatement.PsaFS
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.{PenaltiesService, SchemeService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import javax.inject.Inject
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

      fsConnector.getPsaFS(request.psaIdOrException.id).flatMap {
        psaFS =>
          if (identifier.matches(srnRegex)) {
            schemeService.retrieveSchemeDetails(
              psaId = request.idOrException,
              srn = identifier,
              schemeIdType = "srn"
            ) flatMap {
              schemeDetails =>
                val filteredPsaFS: Seq[PsaFS] =
                  psaFS.filter(_.pstr == schemeDetails.pstr)

                val penaltyTables: Future[Seq[JsObject]] =
                  penaltiesService.getPsaFsJson(filteredPsaFS, identifier, year.toInt) map {
                    _.filter(_ != Json.obj())
                  }

                penaltyTables flatMap {
                  tables =>
                    val json = viewModel(
                      pstr = schemeDetails.pstr,
                      schemeAssociated = true,
                      tables = tables,
                      args = schemeDetails.schemeName
                    )
                    renderer.render(template = "financialStatement/penalties.njk", json).map(Ok(_))
                }

            }
          } else {
            fiCacheConnector.fetch flatMap {
              case Some(jsValue) =>
                val pstrs: Seq[String] =
                  jsValue.as[Seq[PsaFS]].map(_.pstr)

                penaltiesService.unassociatedSchemes(psaFS, year, request.psaIdOrException.id) flatMap {
                  filteredPsaFS =>
                    val penaltyTables: Future[Seq[JsObject]] =
                      penaltiesService.getPsaFsJson(filteredPsaFS, identifier, year.toInt) map {
                        _.filter(_ != Json.obj())
                      }

                    penaltyTables flatMap {
                      tables =>
                        val json = viewModel(
                          pstr = pstrs(identifier.toInt),
                          schemeAssociated = false,
                          tables = tables
                        )

                        renderer.render(template = "financialStatement/penalties.njk", json).map(Ok(_))
                    }
                }

              case _ =>
                Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
            }
          }
      }
  }
}
