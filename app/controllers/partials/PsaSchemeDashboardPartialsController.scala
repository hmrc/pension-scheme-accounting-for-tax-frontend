/*
 * Copyright 2024 HM Revenue & Customs
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
import models.SchemeDetails
import models.requests.IdentifierRequest
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.{PsaSchemePartialService, SchemeService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import viewmodels.CardViewModel
import views.html.partials.SchemePaymentsAndChargesPartialView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PsaSchemeDashboardPartialsController @Inject()(
    identify: IdentifierAction,
    override val messagesApi: MessagesApi,
    val controllerComponents: MessagesControllerComponents,
    schemeService: SchemeService,
    financialStatementConnector: FinancialStatementConnector,
    service: PsaSchemePartialService,
    allowAccess: AllowAccessActionProviderForIdentifierRequest,
    view: SchemePaymentsAndChargesPartialView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  private val logger = Logger(classOf[PsaSchemeDashboardPartialsController])


  def psaSchemeDashboardAFTTilePartial(srn: String): Action[AnyContent] = (identify andThen allowAccess(Some(srn))).async {
    implicit request =>
          schemeService.retrieveSchemeDetails(request.idOrException, srn).flatMap { schemeDetails =>
              service.aftCardModel(schemeDetails, srn).flatMap { cards =>
                Future.successful(Ok(view(cards)))
            }
        }
  }


  def psaSchemeDashboardFinInfoPartial(srn: String): Action[AnyContent] = (identify andThen allowAccess(Some(srn))).async {
    implicit request =>
      schemeService.retrieveSchemeDetails(request.idOrException, srn).flatMap { schemeDetails =>
          getFinancialOverviewTile(srn, schemeDetails).flatMap { cards =>
            Future.successful(Ok(view(cards)))
        }
      }
  }

  private def getFinancialOverviewTile(srn: String, schemeDetails: SchemeDetails)(implicit request: IdentifierRequest[AnyContent]) = {
    financialStatementConnector.getSchemeFS(schemeDetails.pstr).map { schemeFSDetail =>
      val paymentsAndCharges: Seq[CardViewModel] = service.paymentsAndCharges(schemeFSDetail.seqSchemeFSDetail, srn, schemeDetails.pstr)
      logger.debug(s"AFT service returned partial for psa scheme dashboard with aft tile- ${Json.toJson(paymentsAndCharges)}")
      paymentsAndCharges
    }
  }
}
