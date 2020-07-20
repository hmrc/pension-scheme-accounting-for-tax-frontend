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
import helpers.FormatHelper
import javax.inject.Inject
import models.LocalDateBinder._
import models.financialStatement.SchemeFSChargeType.{PSS_AFT_RETURN, PSS_OTC_AFT_RETURN}
import models.financialStatement.{SchemeFS, SchemeFSChargeType}
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import renderer.Renderer
import services.SchemeService
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Content, Html, NunjucksSupport, SummaryList}
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}

import scala.concurrent.ExecutionContext

class PaymentsAndChargeDetailsController @Inject()(override val messagesApi: MessagesApi,
                                                   identify: IdentifierAction,
                                                   val controllerComponents: MessagesControllerComponents,
                                                   config: FrontendAppConfig,
                                                   schemeService: SchemeService,
                                                   financialStatementConnector: FinancialStatementConnector,
                                                   renderer: Renderer)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def onPageLoad(srn: String, startDate: LocalDate, chargeType: SchemeFSChargeType, chargeReference: String): Action[AnyContent] =
    identify.async { implicit request =>
      schemeService.retrieveSchemeDetails(request.psaId.id, srn).flatMap { schemeDetails =>
        financialStatementConnector.getSchemeFS(schemeDetails.pstr).flatMap { schemeFs =>
          val filteredSchemeFs = schemeFs.find(_.chargeReference == chargeReference)

          renderer
            .render(template = "paymentsAndCharges/paymentsAndChargeDetails.njk", summaryListData(srn, filteredSchemeFs, schemeDetails.schemeName))
            .map(Ok(_))
        }
      }
    }

  def summaryListData(srn: String, filteredSchemeFs: Option[SchemeFS], schemeName: String)(implicit messages: Messages): JsObject = {
    filteredSchemeFs match {
      case Some(schemeFS) =>
        val htmlInsetText = (schemeFS.dueDate, schemeFS.amountDue > 0) match {
          case (Some(date), true) =>
            Html(
              s"<h2 class=govuk-heading-s>${messages("paymentsAndCharges.chargeDetails.interestAccruing")}</h2>" +
              s"<p class=govuk-body>${messages("paymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate", date.format(dateFormatterDMY))}" +
              s"<span class=govuk-!-display-block><a class=govuk-link href=${controllers.paymentsAndCharges.routes.PaymentsAndChargesInterestController.
                onPageLoad(srn, schemeFS.periodStartDate, schemeFS.chargeType, schemeFS.chargeReference).url}>" +
              s"${messages("paymentsAndCharges.chargeDetails.interest.breakdown")}</a></span></p>"
            )
          case (Some(date), false) =>
            Html(
              s"<h2 class=govuk-heading-s>${messages("paymentsAndCharges.chargeDetails.interestAccrued")}</h2>" +
                s"<p class=govuk-body>${messages("paymentsAndCharges.chargeDetails.amount.paid.after.dueDate", date.format(dateFormatterDMY))}</p>"
            )
          case _ =>
            Html("")
        }

        Json.obj(
          fields = "chargeDetailsList" -> (originalAmountRow(schemeFS) ++ paymentsAndCreditsRow(schemeFS)
            ++ stoodOverAmountRow(schemeFS) ++ totalAmountDueRow(schemeFS)),
          "tableHeader" -> messages("paymentsAndCharges.caption",
                                    schemeFS.periodStartDate.format(dateFormatterStartDate),
                                    schemeFS.periodEndDate.format(dateFormatterDMY)),
          "schemeName" -> schemeName,
          "chargeType" -> schemeFS.chargeType.toString,
          "chargeReferenceTextMessage" -> (if (schemeFS.totalAmount < 0) {
                                             messages("paymentsAndCharges.credit.information",
                                                      s"${FormatHelper.formatCurrencyAmountAsString(schemeFS.totalAmount.abs)}")
                                           } else {
                                             messages("paymentsAndCharges.chargeDetails.chargeReference", schemeFS.chargeReference)
                                           }),
          "isPaymentOverdue" -> (schemeFS.amountDue > 0 && (schemeFS.chargeType == PSS_AFT_RETURN || schemeFS.chargeType == PSS_OTC_AFT_RETURN)),
          "insetText" -> htmlInsetText,
          "interest" -> schemeFS.accruedInterestTotal,
          "returnUrl" -> config.managePensionsSchemeSummaryUrl.format(srn)
        )
      case _ =>
        Json.obj()
    }
  }

  private def originalAmountRow(schemeFS: SchemeFS)(implicit messages: Messages): Seq[SummaryList.Row] = {
    val credit = if (schemeFS.totalAmount < 0) messages("paymentsAndCharges.credit") else ""
    Seq(
      Row(
        key = Key(msg"paymentsAndCharges.chargeDetails.originalChargeAmount", classes = Seq("govuk-!-padding-left-0", "govuk-!-width-three-quarters")),
        value = Value(
          Literal(s"${FormatHelper.formatCurrencyAmountAsString(schemeFS.totalAmount.abs)} $credit"),
          classes = Seq(if (schemeFS.totalAmount < 0) "" else "govuk-!-width-one-quarter", "govuk-table__cell--numeric")
        ),
        actions = Nil
      ))
  }

  private def paymentsAndCreditsRow(schemeFS: SchemeFS): Seq[SummaryList.Row] = {
    if (schemeFS.totalAmount - schemeFS.amountDue - schemeFS.stoodOverAmount > 0) {
      Seq(
        Row(
          key = Key(msg"paymentsAndCharges.chargeDetails.payments", classes = Seq("govuk-!-padding-left-0", "govuk-!-width-three-quarters")),
          value = Value(
            Literal(s"-${FormatHelper.formatCurrencyAmountAsString(schemeFS.totalAmount - schemeFS.amountDue - schemeFS.stoodOverAmount)}"),
            classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")
          ),
          actions = Nil
        ))
    } else {
      Nil
    }
  }

  private def stoodOverAmountRow(schemeFS: SchemeFS): Seq[SummaryList.Row] = {
    if (schemeFS.stoodOverAmount > 0) {
      Seq(
        Row(
          key = Key(msg"paymentsAndCharges.chargeDetails.stoodOverAmount", classes = Seq("govuk-!-padding-left-0", "govuk-!-width-three-quarters")),
          value = Value(
            Literal(s"-${FormatHelper.formatCurrencyAmountAsString(schemeFS.stoodOverAmount)}"),
            classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")
          ),
          actions = Nil
        ))
    } else {
      Nil
    }
  }

  private def totalAmountDueRow(schemeFS: SchemeFS): Seq[SummaryList.Row] = {
    val amountDueKey: Content = (schemeFS.dueDate, schemeFS.amountDue > 0) match {
      case (Some(date), true) => msg"paymentsAndCharges.chargeDetails.amountDue".withArgs(date.format(dateFormatterDMY))
      case _                  => msg"paymentsAndCharges.chargeDetails.noAmountDue"
    }
    if (schemeFS.totalAmount > 0) {
      Seq(
        Row(
          key =
            Key(amountDueKey,
                classes = Seq("govuk-table__cell--numeric", "govuk-!-padding-right-0", "govuk-!-width-three-quarters", "govuk-!-font-weight-bold")),
          value = Value(
            Literal(s"${FormatHelper.formatCurrencyAmountAsString(schemeFS.amountDue)}"),
            classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric", "govuk-!-font-weight-bold")
          ),
          actions = Nil
        ))
    } else {
      Nil
    }
  }
}
