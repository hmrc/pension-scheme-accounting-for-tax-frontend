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

package controllers.financialStatement.paymentsAndCharges

import config.FrontendAppConfig
import controllers.actions._
import helpers.FormatHelper
import models.ChargeDetailsFilter
import models.financialStatement.PaymentOrChargeType.{AccountingForTaxCharges, getPaymentOrChargeType}
import models.financialStatement.SchemeFSChargeType.{PSS_AFT_RETURN, PSS_AFT_RETURN_INTEREST, PSS_OTC_AFT_RETURN_INTEREST}
import models.financialStatement.{PaymentOrChargeType, SchemeFS}
import models.requests.IdentifierRequest
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import renderer.Renderer
import services.paymentsAndCharges.PaymentsAndChargesService
import uk.gov.hmrc.domain.{PsaId, PspId}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{NunjucksSupport, SummaryList}
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PaymentsAndChargesInterestController @Inject()(
                                                      override val messagesApi: MessagesApi,
                                                      identify: IdentifierAction,
                                                      allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                                      val controllerComponents: MessagesControllerComponents,
                                                      config: FrontendAppConfig,
                                                      paymentsAndChargesService: PaymentsAndChargesService,
                                                      renderer: Renderer
                                                    )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val logger = Logger(classOf[PaymentsAndChargesInterestController])

  def onPageLoad(srn: String, period: String, index: String, paymentOrChargeType: PaymentOrChargeType, journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess()).async {
    implicit request =>
      paymentsAndChargesService.getPaymentsForJourney(request.idOrException, srn, journeyType).flatMap { paymentsCache =>
        val schemeFS: Seq[SchemeFS] = getFilteredPayments(paymentsCache.schemeFS, period, paymentOrChargeType)

        buildPage(schemeFS, period, index, paymentsCache.schemeDetails.schemeName, srn, paymentOrChargeType, journeyType)
      }
  }

  private def getFilteredPayments(payments: Seq[SchemeFS], period: String, paymentOrChargeType: PaymentOrChargeType): Seq[SchemeFS] =
    if(paymentOrChargeType == AccountingForTaxCharges) {
      val startDate: LocalDate = LocalDate.parse(period)
      payments.filter(p => getPaymentOrChargeType(p.chargeType) == AccountingForTaxCharges).filter(_.periodStartDate == startDate)
    } else {
      payments.filter(p => getPaymentOrChargeType(p.chargeType) == paymentOrChargeType).filter(_.periodEndDate.getYear == period.toInt)
    }

  private def buildPage(
                         filteredSchemeFS: Seq[SchemeFS],
                         period: String,
                         index: String,
                         schemeName: String,
                         srn: String,
                         paymentOrChargeType: PaymentOrChargeType,
                         journeyType: ChargeDetailsFilter
                       )(
                         implicit request: IdentifierRequest[AnyContent]
                       ): Future[Result] = {

    val chargeRefs: Seq[String] = filteredSchemeFS.map(_.chargeReference)
    val originalAmountUrl = controllers.financialStatement.paymentsAndCharges.routes.PaymentsAndChargeDetailsController
      .onPageLoad(srn, period, index, paymentOrChargeType, journeyType).url
    if (chargeRefs.size > index.toInt) {
      filteredSchemeFS.find(_.chargeReference == chargeRefs(index.toInt)) match {
        case Some(schemeFs) =>
          renderer.render(
            template = "financialStatement/paymentsAndCharges/paymentsAndChargeInterest.njk",
            ctx = summaryListData(srn, schemeFs, schemeName, request.psaId, request.pspId, originalAmountUrl)
          ).map(Ok(_))
        case _ =>
          logger.warn(s"No Payments and Charge details " +
            s"found for the selected charge reference ${chargeRefs(index.toInt)}")
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
      }
    } else {

      logger.warn(
        "[paymentsAndCharges.PaymentsAndChargesInterestController][IndexOutOfBoundsException]:" +
          s"index $period/$index of attempted"
      )
      Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }

  }

  def summaryListData(srn: String, schemeFS: SchemeFS, schemeName: String,
                      psaId: Option[PsaId], pspId: Option[PspId], originalAmountUrl: String)
                     (implicit messages: Messages): JsObject =
        Json.obj(
          fields = "chargeDetailsList" -> getSummaryListRows(schemeFS),
          "tableHeader" -> messages("paymentsAndCharges.caption",
            schemeFS.periodStartDate.format(dateFormatterStartDate),
            schemeFS.periodEndDate.format(dateFormatterDMY)),
          "schemeName" -> schemeName,
          "accruedInterest" -> schemeFS.accruedInterestTotal,
          "chargeType" -> (
            if (schemeFS.chargeType == PSS_AFT_RETURN) {
              PSS_AFT_RETURN_INTEREST.toString
            } else {
              PSS_OTC_AFT_RETURN_INTEREST.toString
            }
            ),
          "originalAmountUrl" -> originalAmountUrl,
          "returnUrl" -> config.schemeDashboardUrl(psaId, pspId).format(srn)
        )

  private def getSummaryListRows(schemeFS: SchemeFS): Seq[SummaryList.Row] = {
    Seq(
      Row(
        key = Key(msg"paymentsAndCharges.interest", classes = Seq("govuk-!-padding-left-0", "govuk-!-width-three-quarters")),
        value = Value(
          Literal(s"${FormatHelper.formatCurrencyAmountAsString(schemeFS.accruedInterestTotal)}"),
          classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")
        ),
        actions = Nil
      ),
      Row(
        key = Key(
          msg"paymentsAndCharges.interestFrom".withArgs(schemeFS.periodEndDate.plusDays(46).format(dateFormatterDMY)),
          classes = Seq("govuk-table__cell--numeric", "govuk-!-padding-right-0", "govuk-!-width-three-quarters", "govuk-!-font-weight-bold")
        ),
        value = Value(
          Literal(s"${FormatHelper.formatCurrencyAmountAsString(schemeFS.accruedInterestTotal)}"),
          classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric", "govuk-!-font-weight-bold")
        ),
        actions = Nil
      )
    )
  }
}
