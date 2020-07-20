/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers.paymentsAndCharges

import java.time.LocalDate

import config.FrontendAppConfig
import connectors.FinancialStatementConnector
import controllers.actions._
import dateOrdering._
import helpers.FormatHelper
import javax.inject.Inject
import models.LocalDateBinder._
import models.financialStatement.SchemeFS
import models.financialStatement.SchemeFSChargeType.{PSS_AFT_RETURN, PSS_AFT_RETURN_INTEREST, PSS_OTC_AFT_RETURN, PSS_OTC_AFT_RETURN_INTEREST}
import models.viewModels.paymentsAndCharges.PaymentAndChargeStatus.{InterestIsAccruing, NoStatus, PaymentOverdue}
import models.viewModels.paymentsAndCharges.{PaymentsAndChargesDetails, PaymentsAndChargesTable}
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc._
import renderer.Renderer
import services.SchemeService
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Html, NunjucksSupport}
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}
import viewmodels.Table
import viewmodels.Table.Cell

import scala.concurrent.ExecutionContext

class PaymentsAndChargesController @Inject()(override val messagesApi: MessagesApi,
                                             identify: IdentifierAction,
                                             allowAccess: AllowAccessActionProvider,
                                             requireData: DataRequiredAction,
                                             val controllerComponents: MessagesControllerComponents,
                                             config: FrontendAppConfig,
                                             schemeService: SchemeService,
                                             financialStatementConnector: FinancialStatementConnector,
                                             renderer: Renderer)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def onPageLoad(srn: String, year: Int): Action[AnyContent] =
    identify.async { implicit request =>
      schemeService.retrieveSchemeDetails(request.psaId.id, srn).flatMap { schemeDetails =>
        financialStatementConnector.getSchemeFS(schemeDetails.pstr).flatMap { schemeFs =>
          val schemeFSForSelectedYear = schemeFs.filter(_.periodStartDate.getYear == year)

          val SchemeFSMapWithPeriodStartDate: Seq[(LocalDate, Seq[SchemeFS])] =
            schemeFSForSelectedYear.groupBy(_.periodStartDate).toSeq.sortWith(_._1 < _._1)

          val tableOfPaymentsAndCharges: Seq[PaymentsAndChargesTable] = getSeqOfPaymentsAndCharges(SchemeFSMapWithPeriodStartDate, srn)

          val json = Json.obj(
            "seqPaymentsAndChargesTable" -> tableOfPaymentsAndCharges,
            "schemeName" -> schemeDetails.schemeName,
            "returnUrl" -> config.managePensionsSchemeSummaryUrl.format(srn)
          )
          renderer.render(template = "paymentsAndCharges/paymentsAndCharges.njk", json).map(Ok(_))
        }
      }
    }

  private def getSeqOfPaymentsAndCharges(paymentsAndChargesForAGivenPeriod: Seq[(LocalDate, Seq[SchemeFS])], srn: String)(
      implicit messages: Messages): Seq[PaymentsAndChargesTable] = {

    paymentsAndChargesForAGivenPeriod.map { paymentsAndCharges =>
      val seqPaymentsAndCharges = paymentsAndCharges._2

      val seqPayments = seqPaymentsAndCharges.flatMap { details =>
        val validChargeTypes = details.chargeType == PSS_AFT_RETURN || details.chargeType == PSS_OTC_AFT_RETURN
        val redirectChargeDetailsUrl = controllers.paymentsAndCharges.routes.PaymentsAndChargeDetailsController
          .onPageLoad(srn, details.periodStartDate, details.chargeType, details.chargeReference)
          .url
        (validChargeTypes, details.amountDue > 0) match {
          case (true, true) if details.accruedInterestTotal > 0 =>
            createPaymentAndChargesWithInterest(details, srn)
          case (true, _) if details.totalAmount < 0 =>
            Seq(
              PaymentsAndChargesDetails(
                details.periodStartDate,
                details.periodEndDate,
                details.chargeType.toString,
                messages("paymentsAndCharges.chargeReference.None"),
                messages("paymentsAndCharges.amountDue.in.credit"),
                NoStatus,
                redirectChargeDetailsUrl
              ))
          case _ =>
            Seq(
              PaymentsAndChargesDetails(
                details.periodStartDate,
                details.periodEndDate,
                details.chargeType.toString,
                details.chargeReference,
                s"${FormatHelper.formatCurrencyAmountAsString(details.amountDue)}",
                NoStatus,
                redirectChargeDetailsUrl
              ))
        }
      }

      val startDate = seqPayments.headOption.map(_.startDate.format(dateFormatterStartDate)).getOrElse("")
      val endDate = seqPayments.headOption.map(_.endDate.format(dateFormatterDMY)).getOrElse("")

      mapToTable(startDate, endDate, seqPayments, srn)
    }
  }

  private def createPaymentAndChargesWithInterest(details: SchemeFS, srn: String)(implicit messages: Messages): Seq[PaymentsAndChargesDetails] = {
    val interestChargeType = if (details.chargeType == PSS_AFT_RETURN) PSS_AFT_RETURN_INTEREST else PSS_OTC_AFT_RETURN_INTEREST
    val redirectUrl = controllers.paymentsAndCharges.routes.PaymentsAndChargeDetailsController
      .onPageLoad(srn, details.periodStartDate, details.chargeType, details.chargeReference)
      .url
    Seq(
      PaymentsAndChargesDetails(
        details.periodStartDate,
        details.periodEndDate,
        details.chargeType.toString,
        details.chargeReference,
        s"${FormatHelper.formatCurrencyAmountAsString(details.amountDue)}",
        PaymentOverdue,
        redirectUrl
      ),
      PaymentsAndChargesDetails(
        details.periodStartDate,
        details.periodEndDate,
        interestChargeType.toString,
        messages("paymentsAndCharges.chargeReference.toBeAssigned"),
        s"${FormatHelper.formatCurrencyAmountAsString(details.accruedInterestTotal)}",
        InterestIsAccruing,
        redirectUrl
      )
    )
  }

  private def mapToTable(startDate: String, endDate: String, allPayments: Seq[PaymentsAndChargesDetails], srn: String)(
      implicit messages: Messages): PaymentsAndChargesTable = {

    val caption = messages("paymentsAndCharges.caption", startDate, endDate)

    val head = Seq(
      Cell(msg"paymentsAndCharges.chargeType.table", classes = Seq("govuk-!-width-two-thirds-quarter")),
      Cell(msg"paymentsAndCharges.totalDue.table", classes = Seq("govuk-!-width-one-quarter", "govuk-!-font-weight-bold")),
      Cell(msg"paymentsAndCharges.chargeReference.table", classes = Seq("govuk-!-width-one-quarter", "govuk-!-font-weight-bold")),
      Cell(msg"", classes = Seq("govuk-!-font-weight-bold"))
    )

    val rows = allPayments.map { data =>
      val htmlStatus = data.status match {
        case InterestIsAccruing => Html(s"<span class='govuk-tag govuk-tag--blue'>${data.status.toString}</span>")
        case PaymentOverdue     => Html(s"<span class='govuk-tag govuk-tag--red'>${data.status.toString}</span>")
        case _                  => Html("")
      }

      val htmlChargeType = Html(
        s"<a id=linkId class=govuk-link href=" +
          s"${data.redirectUrl}>" +
          s"${data.chargeType}" +
          s"<span class=govuk-visually-hidden>${messages(s"paymentsAndCharges.visuallyHiddenText", data.chargeReference)}</span> </a>")

      Seq(
        Cell(htmlChargeType, classes = Seq("govuk-!-width-two-thirds-quarter")),
        Cell(Literal(data.amountDue), classes = Seq("govuk-!-width-one-quarter")),
        Cell(Literal(s"${data.chargeReference}"), classes = Seq("govuk-!-width-one-quarter")),
        Cell(htmlStatus, classes = Nil)
      )
    }

    PaymentsAndChargesTable(
      caption,
      Table(
        head = head,
        rows = rows,
        attributes = Map("role" -> "grid", "aria-describedby" -> caption)
      )
    )
  }
}
