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

package controllers.financialOverview.scheme

import config.FrontendAppConfig
import controllers.actions._
import helpers.FormatHelper
import models.ChargeDetailsFilter
import models.financialStatement.PaymentOrChargeType.{AccountingForTaxCharges, getPaymentOrChargeType}
import models.financialStatement.SchemeFSChargeType.getInterestChargeTypeText
import models.financialStatement.{PaymentOrChargeType, SchemeFSDetail}
import models.requests.IdentifierRequest
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc._
import services.financialOverview.scheme.PaymentsAndChargesService
import uk.gov.hmrc.domain.{PsaId, PspId}
import uk.gov.hmrc.govukfrontend.views.Aliases.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.HtmlContent
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{Key, SummaryListRow, Value}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.DateHelper.formatDateDMY
import viewmodels.InterestDetailsViewModel
import views.html.financialOverview.scheme.PaymentsAndChargeInterestView

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PaymentsAndChargesInterestController @Inject()(
                                                      override val messagesApi: MessagesApi,
                                                      identify: IdentifierAction,
                                                      allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                                      val controllerComponents: MessagesControllerComponents,
                                                      paymentsAndChargesService: PaymentsAndChargesService,
                                                      config: FrontendAppConfig,
                                                      view: PaymentsAndChargeInterestView
                                                    )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  private val logger = Logger(classOf[PaymentsAndChargesInterestController])

  def onPageLoad(srn: String, period: String, index: String, paymentOrChargeType: PaymentOrChargeType,
                 version: Option[Int], submittedDate: Option[String], journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess(Some(srn))).async {
      implicit request =>
        paymentsAndChargesService.getPaymentsForJourney(request.idOrException, srn, journeyType, request.isLoggedInAsPsa).flatMap { paymentsCache =>

          val schemeFSDetail: Seq[SchemeFSDetail] = getFilteredPayments(paymentsCache.schemeFSDetail, period, paymentOrChargeType)

          buildPage(schemeFSDetail, period, index, paymentsCache.schemeDetails.schemeName, srn, paymentOrChargeType, version, submittedDate, journeyType)
        }
    }

  private def getFilteredPayments(payments: Seq[SchemeFSDetail], period: String, paymentOrChargeType: PaymentOrChargeType): Seq[SchemeFSDetail] =
    if (paymentOrChargeType == AccountingForTaxCharges) {
      val startDate: LocalDate = LocalDate.parse(period)
      payments.filter(p => getPaymentOrChargeType(p.chargeType) == AccountingForTaxCharges).filter(_.periodStartDate.contains(startDate))
    } else {
      payments.filter(p => getPaymentOrChargeType(p.chargeType) == paymentOrChargeType).filter(_.periodEndDate.exists(_.getYear == period.toInt))
    }

  //scalastyle:off parameter.number
  private def buildPage(
                         filteredSchemeFS: Seq[SchemeFSDetail],
                         period: String,
                         index: String,
                         schemeName: String,
                         srn: String,
                         paymentOrChargeType: PaymentOrChargeType,
                         version: Option[Int],
                         submittedDate: Option[String],
                         journeyType: ChargeDetailsFilter
                       )(
                         implicit request: IdentifierRequest[AnyContent]
                       ): Future[Result] = {
    filteredSchemeFS.find(_.index == index.toInt) match {
      case Some(schemeFs) =>
        val originalAmountUrl = routes.PaymentsAndChargeDetailsController.onPageLoad(srn, period, index,
          paymentOrChargeType, version, submittedDate, journeyType).url

          Future.successful(Ok(
            view(
              summaryListData(srn, request.psaId, request.pspId, schemeFs, schemeName, originalAmountUrl, version, journeyType)
            )
          ))
      case _ =>
        logger.warn(s"Scheme not found for index $index")
        Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

  private def summaryListData(srn: String, psaId: Option[PsaId], pspId: Option[PspId], schemeFSDetail: SchemeFSDetail, schemeName: String,
                              originalAmountUrl: String, version: Option[Int], journeyType: ChargeDetailsFilter)
                             (implicit messages: Messages): InterestDetailsViewModel = {

    val htmlInsetText =
      HtmlContent(
        s"<p class=govuk-body>${messages("paymentsAndCharges.interest.chargeReference.text1")}" +
          s" <span><a id='breakdown' class=govuk-link href=$originalAmountUrl>" +
          s" ${messages("paymentsAndCharges.interest.chargeReference.linkText")}</a></span>" +
          s" ${messages("paymentsAndCharges.interest.chargeReference.text2")}</p>"
      )

    val interestChargeType     = getInterestChargeTypeText(schemeFSDetail.chargeType)
    val loggedInAsPsa: Boolean = psaId.isDefined

    InterestDetailsViewModel(
      chargeDetailsList        = getSummaryListRows(schemeFSDetail),
      schemeName               = schemeName,
      interestDueAmount        = Some(FormatHelper.formatCurrencyAmountAsString(schemeFSDetail.accruedInterestTotal)),
      accruedInterest          = schemeFSDetail.accruedInterestTotal,
      chargeType               = (version, interestChargeType) match {
          case (Some(value), interestChargeTypeText) =>
            interestChargeTypeText + s" submission $value"
          case (None, interestChargeTypeText) =>
            interestChargeTypeText
        },
      insetText                = htmlInsetText,
      originalAmountUrl        = originalAmountUrl,
      returnLinkBasedOnJourney = paymentsAndChargesService.getReturnLinkBasedOnJourney(journeyType, schemeName),
      returnUrl                = paymentsAndChargesService.getReturnUrl(srn, psaId, pspId, config, journeyType),
      returnDashboardUrl       = if(loggedInAsPsa) {
        Option(config.managePensionsSchemeSummaryUrl).getOrElse("/pension-scheme-summary/%s").format(srn)
      } else {
        Option(config.managePensionsSchemePspUrl).getOrElse("/%s/dashboard/pension-scheme-details").format(srn)
      }
    )
  }

  private def getSummaryListRows(schemeFSDetail: SchemeFSDetail)(implicit messages: Messages): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(
          content = Text(Messages("financialPaymentsAndCharges.chargeReference")),
          classes = "govuk-!-padding-left-0 govuk-!-width-one-quarter"
        ),
        value = Value(
          content = Text(Messages("paymentsAndCharges.chargeReference.toBeAssigned")),
          classes = "govuk-!-width-one-quarter"
        ),
        actions = None
      ),
      SummaryListRow(
        key = Key(
          content = Text(Messages("pension.scheme.interest.tax.period.new")),
          classes = "govuk-!-padding-left-0 govuk-!-width-three-quarters govuk-!-font-weight-bold"
        ),
        value = Value(
          content = Text(
            s"${formatDateDMY(schemeFSDetail.periodStartDate)} to ${formatDateDMY(schemeFSDetail.periodEndDate)}"
          ),
          classes = "govuk-!-width-one-quarter"
        ),
        actions = None
      )
    )
  }
}