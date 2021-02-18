/*
 * Copyright 2021 HM Revenue & Customs
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

import com.google.inject.Inject
import connectors.cache.FinancialInfoCacheConnector
import connectors.{FinancialStatementConnector, ListOfSchemesConnector}
import helpers.FormatHelper
import models.LocalDateBinder._
import models.financialStatement.PsaFS
import models.{ListSchemeDetails, PenaltySchemes}
import play.api.i18n.Messages
import play.api.libs.json.{Json, OFormat, JsObject}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Value, Row}
import uk.gov.hmrc.viewmodels.Table.Cell
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Html, _}
import utils.DateHelper.dateFormatterDMY
import java.time.LocalDate

import scala.concurrent.{Future, ExecutionContext}

class PenaltiesService @Inject()(fsConnector: FinancialStatementConnector,
                                 fiCacheConnector: FinancialInfoCacheConnector,
                                 listOfSchemesConnector: ListOfSchemesConnector) {

  val isPaymentOverdue: PsaFS => Boolean = data => data.amountDue > BigDecimal(0.00) &&
    (data.dueDate.isDefined && data.dueDate.get.isBefore(LocalDate.now()))

  //PENALTIES
  def getPsaFsJson(penalties: Seq[PsaFS], identifier: String, startDate: LocalDate, chargeRefsIndex: String => String)
                  (implicit messages: Messages, ec: ExecutionContext, hc: HeaderCarrier): JsObject = {

    val head: Seq[Cell] = Seq(
      Cell(msg"penalties.column.penalty"),
      Cell(msg"penalties.column.amount"),
      Cell(msg"penalties.column.chargeReference"),
      Cell(Html(s"<span class='govuk-visually-hidden'>${messages("penalties.column.paymentStatus")}</span>"))
    )

    val rows = penalties.filter(_.periodStartDate == startDate).map {
      data =>

          val content = chargeTypeLink(identifier, data, startDate, chargeRefsIndex(data.chargeReference))
            Seq(
              Cell(content, classes = Seq("govuk-!-width-two-thirds-quarter")),
              Cell(Literal(s"${FormatHelper.formatCurrencyAmountAsString(data.amountDue)}"),
                classes = Seq("govuk-!-width-one-quarter")),
              Cell(Literal(data.chargeReference), classes = Seq("govuk-!-width-one-quarter")),
              statusCell(data)
            )
        }

        Json.obj(
          "penaltyTable" -> Table(head = head, rows = rows, attributes = Map("role" -> "table"))
        )
  }

  private def chargeTypeLink(identifier: String, data: PsaFS, startDate: LocalDate, chargeRefsIndex: String)
                            (implicit messages: Messages, ec: ExecutionContext, hc: HeaderCarrier): Html =
          Html(s"<a id=${data.chargeReference} " +
            s"class=govuk-link href=${controllers.financialStatement.penalties.routes
              .ChargeDetailsController.onPageLoad(identifier, startDate, chargeRefsIndex)}>" +
            s"${messages(data.chargeType.toString)}" +
            s"<span class=govuk-visually-hidden>${messages(s"penalties.visuallyHiddenText", data.chargeReference)}</span> </a>")



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
  def penaltySchemes(startDate: String, psaId: String)
                    (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[PenaltySchemes]] =
    for {
      penalties <- getPenaltiesFromCache(psaId)
      listOfSchemes <- getListOfSchemes(psaId)
    } yield {

      val penaltyPstrs: Seq[String] = penalties.filter(_.periodStartDate == LocalDate.parse(startDate)).map(_.pstr)
      val schemesWithPstr: Seq[ListSchemeDetails] = listOfSchemes.filter(_.pstr.isDefined)

      val associatedSchemes: Seq[PenaltySchemes] = schemesWithPstr
        .filter(scheme => penaltyPstrs.contains(scheme.pstr.get))
        .map(x => PenaltySchemes(Some(x.name), x.pstr.get, Some(x.referenceNumber)))

      val unassociatedSchemes: Seq[PenaltySchemes] = penaltyPstrs
        .filter(penaltyPstr => !schemesWithPstr.map(_.pstr.get).contains(penaltyPstr))
        .map(x => PenaltySchemes(None, x, None))

      associatedSchemes ++ unassociatedSchemes
    }

  def unassociatedSchemes(seqPsaFS: Seq[PsaFS], startDate: String, psaId: String)
                         (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[PsaFS]] = {
    for {
      listOfSchemes <- getListOfSchemes(psaId)
      schemesWithPstr = listOfSchemes.filter(_.pstr.isDefined)
    } yield
      seqPsaFS
        .filter(_.periodStartDate == LocalDate.parse(startDate))
        .filter(psaFS => !schemesWithPstr.map(_.pstr.get).contains(psaFS.pstr))
  }

  private def getListOfSchemes(psaId: String)
                              (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[ListSchemeDetails]] =
    listOfSchemesConnector.getListOfSchemes(psaId).map {
      case Right(list) => list.schemeDetails.getOrElse(Nil)
      case _ => Seq.empty[ListSchemeDetails]
    }

  //SELECT YEAR
  def saveAndReturnPenalties(psaId: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[PsaFS]] =
    for {
      penalties <- fsConnector.getPsaFS(psaId)
      _ <- fiCacheConnector.save(Json.toJson(PenaltiesCache(psaId, penalties)))
    } yield penalties


  def getPenaltiesFromCache(psaId: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[PsaFS]] =
    fiCacheConnector.fetch flatMap {
      case Some(jsValue) if jsValue.as[PenaltiesCache].psaId == psaId => Future.successful(jsValue.as[PenaltiesCache].penalties)
      case _ => saveAndReturnPenalties(psaId)
    }

}

case class PenaltiesCache(psaId: String, penalties: Seq[PsaFS])
object PenaltiesCache {
  implicit val format: OFormat[PenaltiesCache] = Json.format[PenaltiesCache]
}
