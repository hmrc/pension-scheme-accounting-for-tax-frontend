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

package services

import java.time.LocalDate

import com.google.inject.Inject
import config.FrontendAppConfig
import connectors.{FinancialStatementConnector, ListOfSchemesConnector}
import helpers.FormatHelper
import models.LocalDateBinder._
import models.{PenaltySchemes, SchemeDetail}
import models.Quarters._
import models.financialStatement.PsaFS
import play.api.i18n.Messages
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Table.Cell
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Html, _}
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}
import controllers.financialStatement.routes.ChargeDetailsController

import scala.concurrent.{ExecutionContext, Future}

class PenaltiesService @Inject()(config: FrontendAppConfig,
                                fsConnector: FinancialStatementConnector,
                                listOfSchemesConnector: ListOfSchemesConnector) {

  val isPaymentOverdue: PsaFS => Boolean = data => data.amountDue > BigDecimal(0.00) &&
    (data.dueDate.isDefined && data.dueDate.get.isBefore(LocalDate.now()))

  //PENALTIES
  def getPsaFsJson(psaFS: Seq[PsaFS], srn: String, year: Int)
                          (implicit messages: Messages): Seq[Table] =
    availableQuarters(year)(config).flatMap { quarter =>
      val startDate = getStartDate(quarter, year)
      val filteredPsaFS = psaFS.filter(_.periodStartDate == startDate)

      if(filteredPsaFS.nonEmpty) {
        Seq(singlePeriodFSMapping(srn, startDate, filteredPsaFS))
      } else {
        Nil
      }
    }

  private def singlePeriodFSMapping(srn: String, startDate: LocalDate, filteredPsaFS: Seq[PsaFS])
                                   (implicit messages: Messages): Table = {

    val caption: Text = msg"penalties.period".withArgs(startDate.format(dateFormatterStartDate), getQuarter(startDate).endDate.format(dateFormatterDMY))

    val head: Seq[Cell] = Seq(
      Cell(msg"penalties.column.penalty", classes = Seq("govuk-!-width-two-thirds-quarter")),
      Cell(msg"penalties.column.amount", classes = Seq("govuk-!-width-one-quarter")),
      Cell(msg"penalties.column.chargeReference", classes = Seq("govuk-!-width-one-quarter")),
      Cell(msg"")
    )

    val rows: Seq[Seq[Cell]] = filteredPsaFS.map { data =>
      Seq(
        Cell(chargeTypeLink(srn, data, startDate), classes = Seq("govuk-!-width-two-thirds-quarter")),
        Cell(Literal(s"${FormatHelper.formatCurrencyAmountAsString(data.amountDue)}"),
          classes = Seq("govuk-!-width-one-quarter")),
        Cell(Literal(data.chargeReference), classes = Seq("govuk-!-width-one-quarter")),
        statusCell(data)
      )
    }

    Table(caption = Some(caption), captionClasses= Seq("govuk-heading-m"), head = head, rows = rows)

  }

  private def chargeTypeLink(srn: String, data: PsaFS, startDate: LocalDate)(implicit messages: Messages): Html =
    Html(
      s"<a id=${data.chargeReference} class=govuk-link href=${ChargeDetailsController.onPageLoad(srn, startDate, data.chargeReference)}>" +
        s"${messages(data.chargeType.toString)}" +
        s"<span class=govuk-visually-hidden>${messages(s"penalties.visuallyHiddenText", data.chargeReference)}</span> </a>")

  private def statusCell(data: PsaFS)(implicit messages: Messages): Cell = {
    if(isPaymentOverdue(data)) {
      Cell(Html(s"<span class='govuk-tag govuk-tag--red'>${messages("penalties.status.paymentOverdue")}</span>"))
    } else {
      Cell(Html(""))
    }
  }

  //CHARGE DETAILS
  def chargeDetailsRows(data: PsaFS): Seq[SummaryList.Row] =
    Seq(
      Row(
        key = Key(Literal(data.chargeType.toString), classes = Seq("govuk-!-width-three-quarters")),
        value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(data.totalAmount)}"),
          classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
      )
    ) ++ paymentRow(data) ++ amountUnderReviewRow(data) ++ totalDueRow(data)

  private def paymentRow(data: PsaFS): Seq[SummaryList.Row] = {
    val paymentAmount: BigDecimal = data.totalAmount - data.outstandingAmount
    if (paymentAmount != BigDecimal(0.00)) {
      Seq(Row(
        key = Key(msg"penalties.chargeDetails.payments", classes = Seq("govuk-!-width-three-quarters")),
        value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(data.totalAmount - data.amountDue - data.stoodOverAmount)}"),
          classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
      )) }
    else {
      Nil
    }
  }

  private def amountUnderReviewRow(data: PsaFS): Seq[SummaryList.Row] = {
    if (data.stoodOverAmount != BigDecimal(0.00)) {
      Seq(Row(
        key = Key(msg"penalties.chargeDetails.amountUnderReview", classes = Seq("govuk-!-width-three-quarters")),
        value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(data.stoodOverAmount)}"),
          classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
      )) }
    else {
      Nil
    }
  }

  private def totalDueRow(data: PsaFS): Seq[SummaryList.Row] = {
    if (data.amountDue > BigDecimal(0.00) && data.dueDate.isDefined) {
      val dueDate: String = data.dueDate.get.format(dateFormatterDMY)
      Seq(Row(
        key = Key(msg"penalties.chargeDetails.totalDueBy".withArgs(dueDate), classes = Seq("govuk-table__header--numeric","govuk-!-padding-right-0")),
        value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(data.amountDue)}"),
          classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
      )) }
    else {
      Seq(Row(
        key = Key(msg"penalties.chargeDetails.totalDue", classes = Seq("govuk-table__header--numeric","govuk-!-padding-right-0")),
        value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(data.amountDue)}"),
          classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
      ))
    }
  }

  //SELECT SCHEME
  def penaltySchemes(year: String, psaId: String)
                            (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[PenaltySchemes]] =
    for {
      penalties <- fsConnector.getPsaFS(psaId)
      listOfSchemes <- getListOfSchemes(psaId)
    } yield {

      val penaltyPstrs: Seq[String] = penalties.filter(_.periodStartDate.getYear == year.toInt).map(_.pstr)
      val schemesWithPstr: Seq[SchemeDetail] = listOfSchemes.filter(_.pstr.isDefined)

      val associatedSchemes: Seq[PenaltySchemes] = schemesWithPstr
        .filter(scheme => penaltyPstrs.contains(scheme.pstr.get))
        .map(x => PenaltySchemes(Some(x.name), x.pstr.get, Some(x.referenceNumber)))

      val unassociatedSchemes: Seq[PenaltySchemes] = penaltyPstrs
        .filter(penaltyPstr => !schemesWithPstr.map(_.pstr.get).contains(penaltyPstr))
        .map(x => PenaltySchemes(None, x, None))

      associatedSchemes ++ unassociatedSchemes
    }

  private def getListOfSchemes(psaId: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[SchemeDetail]] = {
    listOfSchemesConnector.getListOfSchemes(psaId).map {
      case Right(list) => list.schemeDetail.getOrElse(Nil)
      case _ => Seq.empty[SchemeDetail]
    }
  }

}

