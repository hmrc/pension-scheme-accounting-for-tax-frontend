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

package controllers.financialOverview.scheme

import connectors.FinancialStatementConnector
import controllers.actions._
import helpers.FormatHelper
import models.SchemeDetails
import models.financialStatement.{SchemeFS, SchemeFSDetail}
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc._
import renderer.Renderer
import services.SchemeService
import services.financialOverview.PaymentsAndChargesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SchemeFinancialOverviewController @Inject()(identify: IdentifierAction,
                                                  override val messagesApi: MessagesApi,
                                                  val controllerComponents: MessagesControllerComponents,
                                                  schemeService: SchemeService,
                                                  financialStatementConnector: FinancialStatementConnector,
                                                  service: PaymentsAndChargesService,
                                                  renderer: Renderer
                                                    )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val logger = Logger(classOf[SchemeFinancialOverviewController])

  def schemeFinancialOverview(srn: String): Action[AnyContent] = identify.async {
    implicit request =>
      val response = for {
        schemeDetails <- schemeService.retrieveSchemeDetails(request.idOrException, srn, "srn")
        schemeFS <- financialStatementConnector.getSchemeFS(schemeDetails.pstr)
        creditSchemeFS <- financialStatementConnector.getSchemeFSPaymentOnAccount(schemeDetails.pstr)
      } yield {
        renderFinancialOverview(srn, schemeDetails, schemeFS, request, creditSchemeFS.seqSchemeFSDetail)
      }
      response.flatten
  }

  // scalastyle:off parameter.number
  private def renderFinancialOverview(srn: String,
                                      schemeDetails: SchemeDetails,
                                      schemeFS: SchemeFS,
                                      request: RequestHeader,
                                      creditSchemeFSDetail: Seq[SchemeFSDetail]): Future[Result] = {
    val schemeFSDetail = schemeFS.seqSchemeFSDetail
    val schemeName = schemeDetails.schemeName
    val overdueCharges: Seq[SchemeFSDetail] = service.getOverdueCharges(schemeFSDetail)
    val interestCharges: Seq[SchemeFSDetail] = service.getInterestCharges(schemeFSDetail)
    val totalOverdueCharge: BigDecimal = overdueCharges.map(_.amountDue).sum
    val totalInterestAccruing: BigDecimal = interestCharges.map(_.accruedInterestTotal).sum
    val upcomingCharges: Seq[SchemeFSDetail] = service.extractUpcomingCharges(schemeFSDetail)
    val totalUpcomingCharge : BigDecimal = upcomingCharges.map(_.amountDue).sum
    val totalUpcomingChargeFormatted= s"${FormatHelper.formatCurrencyAmountAsString(totalUpcomingCharge)}"
    val totalOverdueChargeFormatted= s"${FormatHelper.formatCurrencyAmountAsString(totalOverdueCharge)}"
    val totalInterestAccruingFormatted= s"${FormatHelper.formatCurrencyAmountAsString(totalInterestAccruing)}"
    val creditBalanceFormatted: String = creditBalanceAmountFormatted(creditSchemeFSDetail)
    logger.debug(s"AFT service returned UpcomingCharge - $totalUpcomingCharge")
    logger.debug(s"AFT service returned OverdueCharge - $totalOverdueCharge")
    logger.debug(s"AFT service returned InterestAccruing - $totalInterestAccruing")

    val creditBalance = getCreditBalanceAmount(creditSchemeFSDetail)

    val requestRefundUrl = schemeFS.inhibitRefundSignal match {
      case true => controllers.financialOverview.routes.RefundUnavailableController.onPageLoad.url
      case false => routes.RequestRefundController.onPageLoad(srn).url
    }


    renderer.render(
      template = "financialOverview/scheme/schemeFinancialOverview.njk",
      ctx = Json.obj("totalUpcomingCharge" -> totalUpcomingChargeFormatted,
        "totalOverdueCharge" -> totalOverdueChargeFormatted,
        "totalInterestAccruing" -> totalInterestAccruingFormatted ,
        "schemeName" -> schemeName,
        "requestRefundUrl" -> requestRefundUrl,
        "overduePaymentLink" -> routes.PaymentsAndChargesController.onPageLoad(srn, schemeDetails.pstr, "overdue").url,
        "duePaymentLink" -> routes.PaymentsAndChargesController.onPageLoad(srn, schemeDetails.pstr, "upcoming").url,
        "allPaymentLink" -> routes.PaymentOrChargeTypeController.onPageLoad(srn, schemeDetails.pstr).url,
        "creditBalanceFormatted" -> creditBalanceFormatted, "creditBalance" -> creditBalance)
    )(request).map(Ok(_))
  }

  def getCreditBalanceAmount(schemeFs: Seq[SchemeFSDetail]): BigDecimal = {
    val sumAmountOverdue = schemeFs.filter(_.dueDate.nonEmpty).map(_.amountDue).sum

    val creditBalanceAmt = if (sumAmountOverdue >= 0) {
      BigDecimal(0.00)
    } else {
      sumAmountOverdue.abs
    }
    creditBalanceAmt
  }
  def creditBalanceAmountFormatted(schemeFs: Seq[SchemeFSDetail]): String = {
    val creditToDisplay = getCreditBalanceAmount(schemeFs)
    s"${FormatHelper.formatCurrencyAmountAsString(creditToDisplay)}"
  }
}
