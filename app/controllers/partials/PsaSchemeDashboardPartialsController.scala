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

package controllers.partials

import connectors.FinancialStatementConnector
import controllers.actions._
import play.api.Logger

import javax.inject.Inject
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.{PsaSchemePartialService, SchemeService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import viewmodels.CardViewModel

import scala.concurrent.ExecutionContext

class PsaSchemeDashboardPartialsController @Inject()(
                                                      identify: IdentifierAction,
                                                      override val messagesApi: MessagesApi,
                                                      val controllerComponents: MessagesControllerComponents,
                                                      schemeService: SchemeService,
                                                      financialStatementConnector: FinancialStatementConnector,
                                                      service: PsaSchemePartialService,
                                                      renderer: Renderer
                                                    )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val logger = Logger(classOf[PsaSchemeDashboardPartialsController])

  def psaSchemeDashboardPartial(srn: String): Action[AnyContent] = identify.async {
    implicit request =>

      schemeService.retrieveSchemeDetails(request.idOrException, srn, "srn").flatMap { schemeDetails =>
        financialStatementConnector.getSchemeFS(schemeDetails.pstr).flatMap { schemeFS =>
          service.aftCardModel(schemeDetails, srn).flatMap { aftModel =>

            val upcomingTile: Seq[CardViewModel] = service.upcomingAftChargesModel(schemeFS, srn)
            val overdueTile: Seq[CardViewModel] = service.overdueAftChargesModel(schemeFS, srn)

            logger.debug(s"AFT service returned partial for psa scheme dashboard with aft tile- ${Json.toJson(aftModel)}")
            logger.debug(s"AFT service returned partial for psa scheme dashboard with aft tile- ${Json.toJson(upcomingTile)}")
            logger.debug(s"AFT service returned partial for psa scheme dashboard with aft tile- ${Json.toJson(overdueTile)}")

            renderer.render(
              template = "partials/psaSchemeDashboardPartial.njk",
              ctx = Json.obj("cards" -> Json.toJson(aftModel ++ upcomingTile ++ overdueTile))
            ).map(Ok(_))
          }
        }
      }

  }
}
