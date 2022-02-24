/*
 * Copyright 2022 HM Revenue & Customs
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

package controllers.financialOverview

import config.FrontendAppConfig
import connectors.{FinancialStatementConnector, MinimalConnector}
import controllers.actions._
import helpers.FormatHelper
import models.financialStatement.PsaFS
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc._
import renderer.Renderer
import services.{AFTPartialService, SchemeService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PsaFinancialOverviewController @Inject()(
                                                appConfig: FrontendAppConfig,
                                                identify: IdentifierAction,
                                                override val messagesApi: MessagesApi,
                                                val controllerComponents: MessagesControllerComponents,
                                                schemeService: SchemeService,
                                                financialStatementConnector: FinancialStatementConnector,
                                                service: AFTPartialService,
                                                renderer: Renderer,
                                                minimalConnector: MinimalConnector
                                                    )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val logger = Logger(classOf[PsaFinancialOverviewController])

  def psaFinancialOverview: Action[AnyContent] = identify.async {
    implicit request =>
      val response = for {
        psaName <- minimalConnector.getPsaNameFromPsaID(request.psaIdOrException.id)
        (psaFS,creditPsaFS) <- financialStatementConnector.getPsaFSWithPaymentOnAccount(request.psaIdOrException.id)
      } yield {
        renderFinancialOverview(psaName, psaFS, request, creditPsaFS)
      }
      response.flatten
  }

  private def renderFinancialOverview(psaName: String, psaFS: Seq[PsaFS],
                                       request: RequestHeader, creditPsaFS: Seq[PsaFS]) (implicit messages: Messages) : Future[Result] = {
    val psaCharges:(String,String,String) = service.retrievePsaCharge(psaFS)
    val creditBalance = service.getCreditBalanceAmount(creditPsaFS)
    val creditBalanceFormatted: String =  s"${FormatHelper.formatCurrencyAmountAsString(creditBalance)}"

    logger.debug(s"AFT service returned UpcomingCharge - ${psaCharges._1}")
    logger.debug(s"AFT service returned OverdueCharge - ${psaCharges._2}")
    logger.debug(s"AFT service returned InterestAccruing - ${psaCharges._3}")

    val creditBalanceBaseUrl = appConfig.creditBalanceRefundLink
    val requestRefundUrl = s"$creditBalanceBaseUrl?requestType=3&psaName=$psaName&availAmt=$creditBalance"

    renderer.render(
      template = "financialOverview/psaFinancialOverview.njk",
      ctx = Json.obj("totalUpcomingCharge" -> psaCharges._1 ,
        "totalOverdueCharge" -> psaCharges._2 ,
        "totalInterestAccruing" -> psaCharges._3 ,
        "psaName" -> psaName, "requestRefundUrl" -> requestRefundUrl,
        "creditBalanceFormatted" ->  creditBalanceFormatted, "creditBalance" -> creditBalance)
    )(request).map(Ok(_))
  }
}