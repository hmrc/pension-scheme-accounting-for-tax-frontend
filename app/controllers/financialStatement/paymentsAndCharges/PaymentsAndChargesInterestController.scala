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

package controllers.financialStatement.paymentsAndCharges

import config.FrontendAppConfig
import controllers.actions._
import helpers.FormatHelper
import models.ChargeDetailsFilter
import models.financialStatement.PaymentOrChargeType.{AccountingForTaxCharges, getPaymentOrChargeType}
import models.financialStatement.SchemeFSChargeType.{PSS_AFT_RETURN, PSS_AFT_RETURN_INTEREST, PSS_OTC_AFT_RETURN_INTEREST}
import models.financialStatement.{PaymentOrChargeType, SchemeFSDetail}
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
import utils.DateHelper

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
    (identify andThen allowAccess(Some(srn))).async {
    implicit request =>
      paymentsAndChargesService.getPaymentsForJourney(request.idOrException, srn, journeyType).flatMap { paymentsCache =>
        val schemeFSDetail: Seq[SchemeFSDetail] = getFilteredPayments(paymentsCache.schemeFSDetail, period, paymentOrChargeType)

        buildPage(schemeFSDetail, period, index, paymentsCache.schemeDetails.schemeName, srn, paymentOrChargeType, journeyType)
      }
  }

  private def getFilteredPayments(payments: Seq[SchemeFSDetail], period: String, paymentOrChargeType: PaymentOrChargeType): Seq[SchemeFSDetail] =
    if(paymentOrChargeType == AccountingForTaxCharges) {
      val startDate: LocalDate = LocalDate.parse(period)
      payments.filter(p => getPaymentOrChargeType(p.chargeType) == AccountingForTaxCharges).filter(_.periodStartDate.contains(startDate))
    } else {
      payments.filter(p => getPaymentOrChargeType(p.chargeType) == paymentOrChargeType).filter(_.periodEndDate.exists(_.getYear == period.toInt))
    }

  private def buildPage(
                         filteredSchemeFS: Seq[SchemeFSDetail],
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

  def summaryListData(srn: String, schemeFSDetail: SchemeFSDetail, schemeName: String,
                      psaId: Option[PsaId], pspId: Option[PspId], originalAmountUrl: String)
                     (implicit messages: Messages): JsObject =
        Json.obj(
          fields = "chargeDetailsList" -> getSummaryListRows(schemeFSDetail),
          "tableHeader" -> messages("paymentsAndCharges.caption",
           DateHelper.formatStartDate(schemeFSDetail.periodStartDate),
            DateHelper.formatDateDMY(schemeFSDetail.periodEndDate)),
          "schemeName" -> schemeName,
          "accruedInterest" -> schemeFSDetail.accruedInterestTotal,
          "chargeType" -> (
            if (schemeFSDetail.chargeType == PSS_AFT_RETURN) {
              PSS_AFT_RETURN_INTEREST.toString
            } else {
              PSS_OTC_AFT_RETURN_INTEREST.toString
            }
            ),
          "originalAmountUrl" -> originalAmountUrl,
          "returnUrl" -> config.schemeDashboardUrl(psaId, pspId).format(srn)
        )

  private def getSummaryListRows(schemeFSDetail: SchemeFSDetail): Seq[SummaryList.Row] = {
    Seq(
      Row(
        key = Key(msg"paymentsAndCharges.interest", classes = Seq("govuk-!-padding-left-0", "govuk-!-width-three-quarters")),
        value = Value(
          Literal(s"${FormatHelper.formatCurrencyAmountAsString(schemeFSDetail.accruedInterestTotal)}"),
          classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")
        ),
        actions = Nil
      ),
      Row(
        key = Key(
          msg"paymentsAndCharges.interestFrom".withArgs(DateHelper.formatDateDMY(schemeFSDetail.periodEndDate.map(_.plusDays(46)))),
          classes = Seq("govuk-table__cell--numeric", "govuk-!-padding-right-0", "govuk-!-width-three-quarters", "govuk-!-font-weight-bold")
        ),
        value = Value(
          Literal(s"${FormatHelper.formatCurrencyAmountAsString(schemeFSDetail.accruedInterestTotal)}"),
          classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric", "govuk-!-font-weight-bold")
        ),
        actions = Nil
      )
    )
  }
}
