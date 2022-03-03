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
import models.SchemeDetails
import models.financialStatement.SchemeFS
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc._
import renderer.Renderer
import services.{PsaSchemePartialService, SchemeService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import viewmodels.CardViewModel

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PsaSchemeFinancialOverviewController @Inject()(
                                                      appConfig: FrontendAppConfig,
                                                      identify: IdentifierAction,
                                                      override val messagesApi: MessagesApi,
                                                      val controllerComponents: MessagesControllerComponents,
                                                      schemeService: SchemeService,
                                                      financialStatementConnector: FinancialStatementConnector,
                                                      service: PsaSchemePartialService,
                                                      renderer: Renderer,
                                                      minimalConnector: MinimalConnector
                                                    )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val logger = Logger(classOf[PsaSchemeFinancialOverviewController])

  def psaSchemeFinancialOverview(srn: String): Action[AnyContent] = identify.async {
    implicit request =>
      val response = for {
        schemeDetails <- schemeService.retrieveSchemeDetails(request.idOrException, srn, "srn")
        psaOrPspName <- minimalConnector.getPsaOrPspName
        schemeFS <- financialStatementConnector.getSchemeFS(schemeDetails.pstr)
        aftModel <- service.aftCardModel(schemeDetails, srn)
        creditSchemeFS <- financialStatementConnector.getSchemeFSPaymentOnAccount(schemeDetails.pstr)
      } yield {
        val isPsa = request.psaId.nonEmpty
        renderFinancialOverview(srn, psaOrPspName, schemeDetails, schemeFS, aftModel, request, creditSchemeFS, isPsa)
      }
      response.flatten
  }

  private def renderFinancialOverview(srn: String, psaOrPspName: String, schemeDetails: SchemeDetails, schemeFS: Seq[SchemeFS],
                                      aftModel: Seq[CardViewModel], request: RequestHeader, creditSchemeFS: Seq[SchemeFS], isPsa: Boolean) (implicit messages: Messages) : Future[Result] = {
    val schemeName = schemeDetails.schemeName
    val upcomingTile: Seq[CardViewModel] = service.upcomingAftChargesModel(schemeFS, srn)
    val overdueTile: Seq[CardViewModel] = service.overdueAftChargesModel(schemeFS, srn)
    val creditBalanceFormatted: String = service.creditBalanceAmountFormatted(creditSchemeFS)
    logger.debug(s"AFT service returned partial for psa scheme financial overview - ${Json.toJson(aftModel)}")
    logger.debug(s"AFT service returned partial for psa scheme financial overview - ${Json.toJson(upcomingTile)}")
    logger.debug(s"AFT service returned partial for psa scheme financial overview - ${Json.toJson(overdueTile)}")

    val pstr = schemeDetails.pstr
    val creditBalance = service.getCreditBalanceAmount(creditSchemeFS)
    val creditBalanceBaseUrl = appConfig.creditBalanceRefundLink
    val requestRefundUrl = if (isPsa){
      s"$creditBalanceBaseUrl?requestType=1&psaName=$psaOrPspName&pstr=$pstr&availAmt=$creditBalance"
    }
    else {
      s"$creditBalanceBaseUrl?requestType=2&pspName=$psaOrPspName&pstr=$pstr&availAmt=$creditBalance"
    }


    renderer.render(
      template = "financialOverview/psaSchemeFinancialOverview.njk",
      ctx = Json.obj("cards" -> Json.toJson(aftModel ++ upcomingTile ++ overdueTile),
        "schemeName" -> schemeName, "requestRefundUrl" -> requestRefundUrl,
        "creditBalanceFormatted" ->  creditBalanceFormatted, "creditBalance" -> creditBalance)
    )(request).map(Ok(_))
  }
}