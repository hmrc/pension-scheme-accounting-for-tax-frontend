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

package controllers

import config.FrontendAppConfig
import controllers.AFTOverviewController._
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, IdentifierAction}
import helpers.FormatHelper.formatCurrencyAmountAsString
import models.AFTQuarter.formatForDisplayOneYear
import models.financialStatement.PaymentOrChargeType.{AccountingForTaxCharges, EventReportingCharges, getPaymentOrChargeType}
import models.financialStatement.{PaymentOrChargeType, SchemeFSDetail}
import models.requests.IdentifierRequest
import models.{AFTQuarter, DisplayQuarter, SchemeDetails}
import play.api.i18n.Lang.logger
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import play.twirl.api.Html
import services.financialOverview.scheme.PaymentsAndChargesService
import services.{QuartersService, SchemeService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import views.html.AFTOverviewView

class AFTOverviewController @Inject()(
                                       identify: IdentifierAction,
                                       allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                       config: FrontendAppConfig,
                                       schemeService: SchemeService,
                                       quartersService: QuartersService,
                                       paymentsAndChargesService: PaymentsAndChargesService,
                                       override val messagesApi: MessagesApi,
                                       val controllerComponents: MessagesControllerComponents,
                                       aftOverviewView: AFTOverviewView
                                     )(implicit ec: ExecutionContext)
  extends FrontendBaseController with I18nSupport {

  def onPageLoad(srn: String): Action[AnyContent] = (identify andThen allowAccess(Some(srn))).async {
    implicit request =>
      (for {
        outstandingAmount <- getOutstandingPaymentAmount(srn, AccountingForTaxCharges)
        schemeDetails <- schemeService.retrieveSchemeDetails(psaId = request.idOrException, srn)
        quartersInProgress <- quartersService.getInProgressQuarters(srn = srn, pstr = schemeDetails.pstr, request.isLoggedInAsPsa)
        allPastYears <- quartersService.getPastYears(pstr = schemeDetails.pstr, srn, request.isLoggedInAsPsa)
        pastYearsAndQuarters <- Future.traverse(displayYears(allPastYears))(
          year => quartersService.getPastQuarters(pstr = schemeDetails.pstr, year = year, srn = srn, request.isLoggedInAsPsa)
            .flatMap(quarters => Future.successful((year, quarters)))
        )
      }
      yield OverviewInfo(schemeDetails, quartersInProgress, pastYearsAndQuarters, outstandingAmount, allPastYears)
        ).flatMap { overviewInfo =>
        Future.successful(Ok(aftOverviewView(
          overviewInfo.schemeDetails.schemeName,
          controllers.routes.YearsController.onPageLoad(srn).url,
          overviewInfo.outstandingAmount,
          linkForOutstandingAmount(srn, overviewInfo.outstandingAmount),
          overviewInfo.quartersInProgress.map(q => textAndLinkForQuarter(formatForDisplayOneYear, q, srn)),
          overviewInfo.pastYearsAndQuarters.map(pYAQ => (pYAQ._1, pYAQ._2.map(q => textAndLinkForQuarter(formatForDisplayOneYear, q, srn)))),
          controllers.routes.PastAftReturnsController.onPageLoad(srn, 0).url,
          config.schemeDashboardUrl(request).format(srn),
          overviewInfo.allPastYears.length
        )))
      }
  }

  private def getOutstandingPaymentAmount(srn: String, chargeTypeVal: PaymentOrChargeType)(implicit messages: Messages,
                                                                                           request: IdentifierRequest[AnyContent]): Future[String] = {
    paymentsAndChargesService.getPaymentsForJourney(request.idOrException, srn, journeyTypeAll, request.isLoggedInAsPsa).map { paymentsCache =>
      val filteredPayments: Seq[SchemeFSDetail] = paymentsCache.schemeFSDetail.filter(p => getPaymentOrChargeType(p.chargeType) == chargeTypeVal)
      val totalDueCharges: BigDecimal = paymentsAndChargesService.getDueCharges(filteredPayments).map(_.amountDue).sum
      val totalInterestCharges: BigDecimal = paymentsAndChargesService.getInterestCharges(filteredPayments).map(_.accruedInterestTotal).sum
      formatCurrencyAmountAsString(totalDueCharges + totalInterestCharges)
    }.recover {
      case e: Exception =>
        logger.error("Failed to get payments for journey", e)
        messages("aftOverview.totalOutstandingNotAvailable")
    }
  }

  def getEROutstandingPaymentAmount(srn: String): Action[AnyContent] = (identify andThen allowAccess(Some(srn))).async {
    implicit request =>
      getOutstandingPaymentAmount(srn, EventReportingCharges).map {
        data => Ok(Html(data))
      }
  }
}

object AFTOverviewController {

  private val maxPastYearsToDisplay: Int  = 3
  private val journeyTypeAll: String  = "all"
  private val nothingOutstanding: String = "£0.00"

  private val displayYears: Seq[Int] => Seq[Int] = seq => seq.sorted.reverse.take(maxPastYearsToDisplay)

  private val linkForQuarter: (String, AFTQuarter) => String = (srn, aftQuarter) =>
    controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, aftQuarter.startDate.toString).url

  private val textAndLinkForQuarter: (AFTQuarter => String, DisplayQuarter, String) => (String, String) = (formatQuarter, displayQuarter, srn) =>
    (formatQuarter(displayQuarter.quarter), linkForQuarter(srn, displayQuarter.quarter))

  private val linkForOutstandingAmount: (String, String) => String = (srn, outstandingAmount) =>
    if (outstandingAmount == nothingOutstanding) {
      controllers.financialOverview.scheme.routes.SchemeFinancialOverviewController.schemeFinancialOverview(srn).url
    } else {
      controllers.financialOverview.scheme.routes.SelectYearController.onPageLoad(srn, AccountingForTaxCharges).url
    }


  private case class OverviewInfo(
                                   schemeDetails: SchemeDetails,
                                   quartersInProgress: Seq[DisplayQuarter],
                                   pastYearsAndQuarters: Seq[(Int, Seq[DisplayQuarter])],
                                   outstandingAmount: String,
                                   allPastYears: Seq[Int]
                                 )

}
