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
import models.financialStatement.{SchemeFS, SchemeFSDetail}
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
  private val psaIdRegex = "^A[0-9]{7}$".r
  private def isPsaId(s:String) = psaIdRegex.findFirstIn(s).isDefined
  def psaSchemeFinancialOverview(srn: String): Action[AnyContent] = identify.async {
    implicit request =>
      val response = for {
        schemeDetails <- schemeService.retrieveSchemeDetails(request.idOrException, srn, "srn")
        psaOrPspName <- minimalConnector.getPsaOrPspName
        schemeFSDetail <- financialStatementConnector.getSchemeFS(schemeDetails.pstr)
        aftModel <- service.aftCardModel(schemeDetails, srn)
        creditSchemeFS <- financialStatementConnector.getSchemeFSPaymentOnAccount(schemeDetails.pstr)
      } yield {
        val isPsa = isPsaId(request.idOrException)
        renderFinancialOverview(srn, psaOrPspName, schemeDetails, schemeFSDetail, aftModel, request, creditSchemeFS.seqSchemeFSDetail, isPsa)
      }
      response.flatten
  }

  //scalastyle:off parameter.number
  private def renderFinancialOverview(srn: String, psaOrPspName: String, schemeDetails: SchemeDetails, schemeFS: SchemeFS,
                                      aftModel: Seq[CardViewModel], request: RequestHeader, creditSchemeFS: Seq[SchemeFSDetail],
                                      isPsa: Boolean)(implicit messages: Messages) : Future[Result] = {
    val schemeFSDetail = schemeFS.seqSchemeFSDetail
    val schemeName = schemeDetails.schemeName
    val upcomingTile: Seq[CardViewModel] = service.upcomingAftChargesModel(schemeFSDetail, srn)
    val overdueTile: Seq[CardViewModel] = service.overdueAftChargesModel(schemeFSDetail, srn)
    val creditBalanceFormatted: String = service.creditBalanceAmountFormatted(creditSchemeFS)
    logger.debug(s"AFT service returned partial for psa scheme financial overview - ${Json.toJson(aftModel)}")
    logger.debug(s"AFT service returned partial for psa scheme financial overview - ${Json.toJson(upcomingTile)}")
    logger.debug(s"AFT service returned partial for psa scheme financial overview - ${Json.toJson(overdueTile)}")

    val pstr = schemeDetails.pstr
    val creditBalance = service.getCreditBalanceAmount(creditSchemeFS)
    val creditBalanceBaseUrl = appConfig.creditBalanceRefundLink
    val requestRefundUrl = (schemeFS.inhibitRefundSignal, isPsa) match {
      case (true, _) => routes.RefundUnavailableController.onPageLoad.url
      case (false, true) =>
        s"$creditBalanceBaseUrl?requestType=1&psaName=$psaOrPspName&pstr=$pstr&availAmt=$creditBalance"
      case (false, false) =>
        s"$creditBalanceBaseUrl?requestType=2&pspName=$psaOrPspName&pstr=$pstr&availAmt=$creditBalance"
    }

    renderer.render(
       template = "financialOverview/psaSchemeFinancialOverview.njk",
        ctx = Json.obj("cards" -> Json.toJson(aftModel ++ upcomingTile ++ overdueTile),
        "schemeName" -> schemeName,
        "requestRefundUrl" -> requestRefundUrl,
         "overduePaymentLink" -> routes.PaymentsAndChargesController.onPageLoad(srn, schemeDetails.pstr, "overdue").url,
         "duePaymentLink" -> routes.PaymentsAndChargesController.onPageLoad(srn, schemeDetails.pstr, "upcoming").url,
         "allPaymentLink" -> routes.PaymentOrChargeTypeController.onPageLoad(srn, schemeDetails.pstr).url,
        "creditBalanceFormatted" ->  creditBalanceFormatted, "creditBalance" -> creditBalance)
    )(request).map(Ok(_))
  }
}
