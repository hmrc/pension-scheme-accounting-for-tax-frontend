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
import connectors.cache.FinancialInfoCacheConnector
import connectors.{FinancialStatementConnector, ListOfSchemesConnector}
import helpers.FormatHelper
import models.LocalDateBinder._
import models.Quarters._
import models.financialStatement.PsaFS
import models.{ListSchemeDetails, PenaltySchemes}
import play.api.i18n.Messages
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Table.Cell
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Html, _}
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}

import scala.concurrent.{ExecutionContext, Future}

class PenaltiesService @Inject()(config: FrontendAppConfig,
                                 fsConnector: FinancialStatementConnector,
                                 fiCacheConnector: FinancialInfoCacheConnector,
                                 listOfSchemesConnector: ListOfSchemesConnector) {

  val isPaymentOverdue: PsaFS => Boolean = data => data.amountDue > BigDecimal(0.00) &&
    (data.dueDate.isDefined && data.dueDate.get.isBefore(LocalDate.now()))

  //PENALTIES
  def getPsaFsJson(psaFS: Seq[PsaFS], identifier: String, year: Int)
                  (implicit messages: Messages, ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[JsObject]] =
    Future.sequence(availableQuarters(year)(config).map { quarter =>
      val startDate = getStartDate(quarter, year)

      val filteredPsaFS: Seq[PsaFS] = psaFS.filter(_.periodStartDate == startDate)

      if (filteredPsaFS.nonEmpty) {
        singlePeriodFSMapping(identifier, startDate, filteredPsaFS)
      } else {
        Future.successful(Json.obj())
      }
    })

  private def singlePeriodFSMapping(identifier: String, startDate: LocalDate, filteredPsaFS: Seq[PsaFS])
                                   (implicit messages: Messages, ec: ExecutionContext, hc: HeaderCarrier): Future[JsObject] = {

    val caption: Text = msg"penalties.period".withArgs(startDate.format(dateFormatterStartDate), getQuarter(startDate).endDate.format(dateFormatterDMY))

    val head: Seq[Cell] = Seq(
      Cell(msg"penalties.column.penalty", classes = Seq("govuk-!-width-two-thirds-quarter")),
      Cell(msg"penalties.column.amount", classes = Seq("govuk-!-width-one-quarter")),
      Cell(msg"penalties.column.chargeReference", classes = Seq("govuk-!-width-one-quarter")),
      Cell(Html(s"<span class='govuk-visually-hidden'>${messages("penalties.column.paymentStatus")}</span>"))
    )

    Future.sequence(filteredPsaFS.map {
      data =>
        chargeTypeLink(identifier, data, startDate).map {
          content =>
            Seq(
              Cell(content, classes = Seq("govuk-!-width-two-thirds-quarter")),
              Cell(Literal(s"${FormatHelper.formatCurrencyAmountAsString(data.amountDue)}"),
                classes = Seq("govuk-!-width-one-quarter")),
              Cell(Literal(data.chargeReference), classes = Seq("govuk-!-width-one-quarter")),
              statusCell(data)
            )
        }
    }) map {
      rows =>
        Json.obj(
          "header" -> caption,
          "penaltyTable" -> Table(head = head, rows = rows, attributes = Map("role" -> "table"))
        )
    }
  }

  private def chargeTypeLink(identifier: String, data: PsaFS, startDate: LocalDate)
                            (implicit messages: Messages, ec: ExecutionContext, hc: HeaderCarrier): Future[Html] = {
    fiCacheConnector.fetch flatMap {
      case Some(jsValue) =>
        val chargeRefsIndex: String =
          jsValue.as[Seq[PsaFS]].map(_.chargeReference).indexOf(data.chargeReference).toString

        Future.successful(Html(
          s"<a id=${data.chargeReference} " +
            s"class=govuk-link href=${controllers.financialStatement.routes.ChargeDetailsController.onPageLoad(identifier, startDate, chargeRefsIndex)}>" +
            s"${messages(data.chargeType.toString)}" +
            s"<span class=govuk-visually-hidden>${messages(s"penalties.visuallyHiddenText", data.chargeReference)}</span> </a>"))
      case _ =>
        Future.successful(Html(""))
    }

  }

  private def statusCell(data: PsaFS)(implicit messages: Messages): Cell = {
    val (classes, content) = (isPaymentOverdue(data), data.amountDue) match {
      case (true, _) => ("govuk-tag govuk-tag--red", messages("penalties.status.paymentOverdue"))
      case (_, amount) if amount == BigDecimal(0.00) =>
        ("govuk-visually-hidden", messages("penalties.status.visuallyHiddenText.noPaymentDue"))
      case _ => ("govuk-visually-hidden", messages("penalties.status.visuallyHiddenText.paymentIsDue"))
    }
    Cell(Html(s"<span class='$classes'>$content</span>"))
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
      ))
    }
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
      ))
    }
    else {
      Nil
    }
  }

  private def totalDueRow(data: PsaFS): Seq[SummaryList.Row] = {
    if (data.amountDue > BigDecimal(0.00) && data.dueDate.isDefined) {
      val dueDate: String = data.dueDate.get.format(dateFormatterDMY)
      Seq(Row(
        key = Key(msg"penalties.chargeDetails.totalDueBy".withArgs(dueDate), classes = Seq("govuk-table__header--numeric", "govuk-!-padding-right-0")),
        value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(data.amountDue)}"),
          classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
      ))
    }
    else {
      Seq(Row(
        key = Key(msg"penalties.chargeDetails.totalDue", classes = Seq("govuk-table__header--numeric", "govuk-!-padding-right-0")),
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
      penaltyPstrs = penalties.filter(_.periodStartDate.getYear == year.toInt).map(_.pstr)
      listOfSchemes <- getListOfSchemes(psaId)
      schemesWithPstr = listOfSchemes.filter(_.pstr.isDefined)
      unassociatedSchemes = penaltyPstrs
        .filter(penaltyPstr => !schemesWithPstr.map(_.pstr.get).contains(penaltyPstr))
        .map(x => PenaltySchemes(None, x, None))
      _ <- fiCacheConnector.save(Json.toJson(penalties))
    } yield {

      val associatedSchemes: Seq[PenaltySchemes] = schemesWithPstr
        .filter(scheme => penaltyPstrs.contains(scheme.pstr.get))
        .map(x => PenaltySchemes(Some(x.name), x.pstr.get, Some(x.referenceNumber)))

      associatedSchemes ++ unassociatedSchemes
    }

  def unassociatedSchemes(seqPsaFS: Seq[PsaFS], year: String, psaId: String)
                         (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[PsaFS]] = {
    for {
      listOfSchemes <- getListOfSchemes(psaId)
      schemesWithPstr = listOfSchemes.filter(_.pstr.isDefined)
    } yield
      seqPsaFS
        .filter(_.periodStartDate.getYear == year.toInt)
        .filter(psaFS => !schemesWithPstr.map(_.pstr.get).contains(psaFS.pstr))
  }

  private def getListOfSchemes(psaId: String)
                              (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[ListSchemeDetails]] =
    listOfSchemesConnector.getListOfSchemes(psaId).map {
      case Right(list) => list.schemeDetails.getOrElse(Nil)
      case _ => Seq.empty[ListSchemeDetails]
    }
}
