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
import services.financialOverview.PaymentsAndChargesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Html, NunjucksSupport, SummaryList}
import utils.DateHelper.dateFormatterDMY

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PaymentsAndChargesInterestController @Inject()(
                                                      override val messagesApi: MessagesApi,
                                                      identify: IdentifierAction,
                                                      allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                                      val controllerComponents: MessagesControllerComponents,
                                                      paymentsAndChargesService: PaymentsAndChargesService,
                                                      renderer: Renderer
                                                    )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val logger = Logger(classOf[PaymentsAndChargesInterestController])

  def onPageLoad(srn: String, pstr: String, period: String, index: String, paymentOrChargeType: PaymentOrChargeType,
                 version: Option[Int], submittedDate: Option[String], journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess()).async {
    implicit request =>
      paymentsAndChargesService.getPaymentsForJourney(request.idOrException, srn, journeyType).flatMap { paymentsCache =>
        val schemeFS: Seq[SchemeFS] = getFilteredPayments(paymentsCache.schemeFS, period, paymentOrChargeType)

        buildPage(schemeFS, period, index,paymentsCache.schemeDetails.schemeName, srn, pstr, paymentOrChargeType, version, submittedDate, journeyType)
      }
  }

  private def getFilteredPayments(payments: Seq[SchemeFS], period: String, paymentOrChargeType: PaymentOrChargeType): Seq[SchemeFS] =
    if(paymentOrChargeType == AccountingForTaxCharges) {
      val startDate: LocalDate = LocalDate.parse(period)
      payments.filter(p => getPaymentOrChargeType(p.chargeType) == AccountingForTaxCharges).filter(_.periodStartDate == startDate)
    } else {
      payments.filter(p => getPaymentOrChargeType(p.chargeType) == paymentOrChargeType).filter(_.periodEndDate.getYear == period.toInt)
    }

  //scalastyle:off parameter.number
  private def buildPage(
                         filteredSchemeFS: Seq[SchemeFS],
                         period: String,
                         index: String,
                         schemeName: String,
                         srn: String,
                         pstr: String,
                         paymentOrChargeType: PaymentOrChargeType,
                         version: Option[Int],
                         submittedDate: Option[String],
                         journeyType: ChargeDetailsFilter
                       )(
                         implicit request: IdentifierRequest[AnyContent]
                       ): Future[Result] = {

    val chargeRefs: Seq[String] = filteredSchemeFS.map(_.chargeReference)
    val originalAmountUrl = controllers.financialOverview.routes.PaymentsAndChargeDetailsController
      .onPageLoad(srn, pstr, period, index, paymentOrChargeType, version, submittedDate, journeyType).url
    if (chargeRefs.size > index.toInt) {
      filteredSchemeFS.find(_.chargeReference == chargeRefs(index.toInt)) match {
        case Some(schemeFs) =>
          renderer.render(
            template = "financialOverview/paymentsAndChargeInterest.njk",
            ctx = summaryListData(srn, pstr, schemeFs, schemeName, originalAmountUrl, version, journeyType)
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

  private def summaryListData(srn: String, pstr: String, schemeFS: SchemeFS, schemeName: String,
                              originalAmountUrl: String, version: Option[Int],
                              journeyType: ChargeDetailsFilter)
                              (implicit messages: Messages): JsObject = {

    val htmlInsetText =
      Html(
        s"<p class=govuk-body>${messages("paymentsAndCharges.interest.chargeReference.text1")}" +
          s" <span><a id='breakdown' class=govuk-link href=$originalAmountUrl>" +
          s" ${messages("paymentsAndCharges.interest.chargeReference.linkText")}</a></span>" +
          s" ${messages("paymentsAndCharges.interest.chargeReference.text2")}</p>"
      )

    Json.obj(
      fields = "chargeDetailsList" -> getSummaryListRows(schemeFS),
      "tableHeader" -> tableHeader(schemeFS),
      "schemeName" -> schemeName,
      "accruedInterest" -> schemeFS.accruedInterestTotal,
      "chargeType" -> (
        (version, schemeFS.chargeType) match {
          case (Some(value), PSS_AFT_RETURN) =>
            PSS_AFT_RETURN_INTEREST.toString + s" submission $value"
          case (Some(value), _) =>
            PSS_OTC_AFT_RETURN_INTEREST.toString + s" submission $value"
          case (None, PSS_AFT_RETURN) =>
            PSS_AFT_RETURN_INTEREST.toString
          case (None, _) =>
            PSS_OTC_AFT_RETURN_INTEREST.toString
        }
        ),
      "insetText" -> htmlInsetText,
      "originalAmountUrl" -> originalAmountUrl,
      "returnLinkBasedOnJourney" -> msg"financialPaymentsAndCharges.returnLink.${journeyType.toString}",
      "returnUrl" -> routes.PaymentsAndChargesController.onPageLoad(srn, pstr, journeyType).url
    )
  }

  private def tableHeader(schemeFS: SchemeFS): String =
    paymentsAndChargesService.setPeriod(
      schemeFS.chargeType,
      schemeFS.periodStartDate,
      schemeFS.periodEndDate
    )

  private def getSummaryListRows(schemeFS: SchemeFS)(implicit messages: Messages): Seq[SummaryList.Row] = {
    val chargeReference : String = schemeFS.chargeReference.isEmpty match {
      case false => schemeFS.chargeReference
      case true => messages("paymentsAndCharges.chargeReference.toBeAssigned")
    }
    Seq(
      Row(
        key = Key(
          content = msg"financialPaymentsAndCharges.chargeReference",
          classes = Seq("govuk-!-padding-left-0", "govuk-!-width-one-half")
        ),
        value = Value(
          content = Literal(chargeReference),
          classes =
            Seq("govuk-!-width-one-quarter")
        ),
        actions = Nil
      ),
      Row(
        key = Key(
          msg"paymentsAndCharges.interestFrom".withArgs(
            schemeFS.periodEndDate.plusDays(46).format(dateFormatterDMY)),
          classes = Seq("govuk-!-padding-left-0", "govuk-!-width-three-quarters", "govuk-!-font-weight-bold")
        ),
        value = Value(
          Literal(s"${FormatHelper.formatCurrencyAmountAsString(schemeFS.accruedInterestTotal)}"),
          classes = Seq("govuk-!-width-one-quarter", "govuk-!-font-weight-bold")
        ),
        actions = Nil
      ))
  }
}
