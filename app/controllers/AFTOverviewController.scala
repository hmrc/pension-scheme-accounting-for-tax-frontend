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
import models.AFTQuarter.{formatForDisplayOneYear, monthDayStringFormat}
import models.financialStatement.PaymentOrChargeType.{AccountingForTaxCharges, getPaymentOrChargeType}
import models.financialStatement.SchemeFSDetail
import models.requests.IdentifierRequest
import models.{AFTQuarter, DisplayQuarter, SchemeDetails}
import play.api.i18n.Lang.logger
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json, OWrites}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.financialOverview.scheme.PaymentsAndChargesService
import services.{QuartersService, SchemeService}
import uk.gov.hmrc.nunjucks.NunjucksSupport
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AFTOverviewController @Inject()(
                                       identify: IdentifierAction,
                                       renderer: Renderer,
                                       allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                       config: FrontendAppConfig,
                                       schemeService: SchemeService,
                                       quartersService: QuartersService,
                                       paymentsAndChargesService: PaymentsAndChargesService,
                                       override val messagesApi: MessagesApi,
                                       val controllerComponents: MessagesControllerComponents
                                     )(implicit ec: ExecutionContext)
  extends FrontendBaseController with I18nSupport with NunjucksSupport {

  def onPageLoad(srn: String): Action[AnyContent] = (identify andThen allowAccess(Some(srn))).async {
    implicit request =>
      (for {
        outstandingAmount <- getOutstandingPaymentAmount(srn)
        schemeDetails <- schemeService.retrieveSchemeDetails(psaId = request.idOrException, schemeIdType = schemeIdType, srn = srn)
        quartersInProgress <- quartersService.getInProgressQuarters(srn = srn, pstr = schemeDetails.pstr)
        allPastYears <- quartersService.getPastYears(pstr = schemeDetails.pstr)
        pastYearsAndQuarters <- Future.traverse(displayYears(allPastYears))(
          year => quartersService.getPastQuarters(pstr = schemeDetails.pstr, year = year).flatMap(quarters => Future.successful(year, quarters))
        )
      }
      yield OverviewInfo(schemeDetails, quartersInProgress, pastYearsAndQuarters, outstandingAmount)
        ).flatMap { overviewInfo =>
        renderer.render(aftOverviewTemplate,
          Json.obj(viewModel ->
            OverviewViewModel(
              returnUrl = config.schemeDashboardUrl(request).format(srn),
              newAftUrl = controllers.routes.YearsController.onPageLoad(srn).url,
              paymentsAndChargesUrl = controllers.financialOverview.scheme.routes.SelectYearController.onPageLoad(srn, AccountingForTaxCharges).url,
              schemeName = overviewInfo.schemeDetails.schemeName,
              outstandingAmount = overviewInfo.outstandingAmount,
              quartersInProgress = overviewInfo.quartersInProgress.map(q => textAndLinkForQuarter(formatForDisplayOneYear, q, srn)),
              pastYearsAndQuarters = overviewInfo.pastYearsAndQuarters.map(pYAQ => (pYAQ._1, pYAQ._2.map(q => textAndLinkForQuarter(monthDayStringFormat, q, srn)))),
              viewAllPastAftsUrl = controllers.routes.PastAftReturnsController.onPageLoad(srn, 0).url
            )
          )
        ).map(Ok(_))
      }.recoverWith {
        case e: Exception => logger.error("Failed to retrieve scheme details or past year details", e)
        renderer.render(
          "error.njk"
        ).map(Ok(_))
      }
  }

  private def getOutstandingPaymentAmount(srn: String)(implicit messages: Messages, request: IdentifierRequest[AnyContent]): Future[String] = {
    paymentsAndChargesService.getPaymentsForJourney(request.idOrException, srn, journeyTypeAll).map { paymentsCache =>
      val filteredPayments: Seq[SchemeFSDetail] = paymentsCache.schemeFSDetail.filter(p => getPaymentOrChargeType(p.chargeType) == AccountingForTaxCharges)
      val totalDueCharges: BigDecimal = paymentsAndChargesService.getDueCharges(filteredPayments).map(_.amountDue).sum
      val totalInterestCharges: BigDecimal = paymentsAndChargesService.getInterestCharges(filteredPayments).map(_.accruedInterestTotal).sum
      formatCurrencyAmountAsString(totalDueCharges + totalInterestCharges)
    }.recover {
      case e: Exception =>
        logger.error("Failed to get payments for journey", e)
        messages("aftOverview.totalOutstandingNotAvailable")
    }
  }
}

object AFTOverviewController {

  private val schemeIdType: String = "srn"
  private val viewModel: String = "viewModel"
  private val aftOverviewTemplate: String = "aftOverview.njk"
  private val maxPastYearsToDisplay: Int  = 3
  private val journeyTypeAll: String  = "all"


  private val displayYears: Seq[Int] => Seq[Int] = seq => seq.sorted.reverse.take(maxPastYearsToDisplay)

  private val linkForQuarter: (String, AFTQuarter) => String = (srn, aftQuarter) =>
    controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, aftQuarter.startDate.toString).url

  private val textAndLinkForQuarter: (AFTQuarter => String, DisplayQuarter, String) => (String, String) = (formatQuarter, displayQuarter, srn) =>
    (formatQuarter(displayQuarter.quarter), linkForQuarter(srn, displayQuarter.quarter))


  private case class OverviewInfo(
                                   schemeDetails: SchemeDetails,
                                   quartersInProgress: Seq[DisplayQuarter],
                                   pastYearsAndQuarters: Seq[(Int, Seq[DisplayQuarter])],
                                   outstandingAmount: String
                                 )

  case class OverviewViewModel(
                                returnUrl: String,
                                newAftUrl: String,
                                paymentsAndChargesUrl: String,
                                schemeName: String,
                                outstandingAmount: String,
                                quartersInProgress: Seq[(String, String)] = Seq.empty,
                                pastYearsAndQuarters: Seq[(Int, Seq[(String, String)])] = Seq.empty,
                                viewAllPastAftsUrl: String
                              )

  private object OverviewViewModel {
    implicit lazy val writes: OWrites[OverviewViewModel] = Json.writes[OverviewViewModel]
  }
}
