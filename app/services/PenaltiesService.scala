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
import controllers.Assets.Redirect
import controllers.financialStatement.penalties.routes._
import helpers.FormatHelper
import models.LocalDateBinder._
import models.financialStatement.PenaltyType._
import models.financialStatement.{PenaltyType, PsaFS}
import models.{ListSchemeDetails, PenaltySchemes}
import play.api.i18n.Messages
import play.api.libs.json.{JsObject, JsSuccess, Json, OFormat}
import play.api.mvc.Result
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Table.Cell
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Html, _}
import utils.DateHelper.dateFormatterDMY

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class PenaltiesService @Inject()(fsConnector: FinancialStatementConnector,
                                 fiCacheConnector: FinancialInfoCacheConnector,
                                 listOfSchemesConnector: ListOfSchemesConnector) {

  val isPaymentOverdue: PsaFS => Boolean = data => data.amountDue > BigDecimal(0.00) && data.dueDate.exists(_.isBefore(LocalDate.now()))

  //PENALTIES
  def getPsaFsJson(penalties: Seq[PsaFS], identifier: String, chargeRefsIndex: String => String, penaltyType: PenaltyType)
                  (implicit messages: Messages): JsObject = {

    val head: Seq[Cell] = Seq(
      Cell(msg"penalties.column.${if(penaltyType == ContractSettlementCharges) "chargeType" else "penaltyType"}"),
      Cell(msg"penalties.column.amount"),
      Cell(msg"penalties.column.chargeReference"),
      Cell(Html(s"<span class='govuk-visually-hidden'>${messages("penalties.column.paymentStatus")}</span>"))
    )

    val rows = penalties.map {
      data =>

         val content = chargeTypeLink(identifier, data, chargeRefsIndex(data.chargeReference))
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

  private def chargeTypeLink(identifier: String, data: PsaFS, chargeRefsIndex: String)
                            (implicit messages: Messages): Html =
          Html(s"<a id=${data.chargeReference} " +
            s"class=govuk-link href=${controllers.financialStatement.penalties.routes
              .ChargeDetailsController.onPageLoad(identifier, chargeRefsIndex)}>" +
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
  def penaltySchemes(startDate: LocalDate, psaId: String, penalties: Seq[PsaFS])
                    (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[PenaltySchemes]] = {

      val filteredPenalties = penalties
        .filter(p => getPenaltyType(p.chargeType) == AccountingForTaxPenalties)
        .filter(_.periodStartDate == startDate)

      penaltySchemes(filteredPenalties, psaId)
    }

  def penaltySchemes(year: Int, psaId: String, penaltyType: PenaltyType, penalties: Seq[PsaFS])
                    (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[PenaltySchemes]] = {

      val filteredPenalties = penalties
        .filter(p => getPenaltyType(p.chargeType) == penaltyType)
        .filter(_.periodStartDate.getYear == year)

      penaltySchemes(filteredPenalties, psaId)
    }

  private def penaltySchemes(filteredPenalties: Seq[PsaFS], psaId: String)
                    (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[PenaltySchemes]] =
    for {
      listOfSchemes <- getListOfSchemes(psaId)
    } yield {

      val penaltyPstrs: Seq[String] = filteredPenalties.map(_.pstr).distinct
      val schemesWithPstr: Seq[ListSchemeDetails] = listOfSchemes.filter(_.pstr.isDefined)

      val associatedSchemes: Seq[PenaltySchemes] = schemesWithPstr
        .filter(scheme => penaltyPstrs.contains(scheme.pstr.get))
        .map(x => PenaltySchemes(Some(x.name), x.pstr.get, Some(x.referenceNumber)))

      val unassociatedSchemes: Seq[PenaltySchemes] = penaltyPstrs
        .filter(penaltyPstr => !schemesWithPstr.map(_.pstr.get).contains(penaltyPstr))
        .map(x => PenaltySchemes(None, x, None))

      associatedSchemes ++ unassociatedSchemes
    }

  def unassociatedSchemes(seqPsaFS: Seq[PsaFS], startDate: LocalDate, psaId: String)
                         (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[PsaFS]] = {
    for {
      listOfSchemes <- getListOfSchemes(psaId)
      schemesWithPstr = listOfSchemes.filter(_.pstr.isDefined)
    } yield
      seqPsaFS
        .filter(_.periodStartDate == startDate)
        .filter(psaFS => !schemesWithPstr.map(_.pstr.get).contains(psaFS.pstr))
  }

  def unassociatedSchemes(seqPsaFS: Seq[PsaFS], year: Int, psaId: String)
                         (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[PsaFS]] = {
    for {
      listOfSchemes <- getListOfSchemes(psaId)
      schemesWithPstr = listOfSchemes.filter(_.pstr.isDefined)
    } yield
      seqPsaFS
        .filter(_.periodStartDate.getYear == year)
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
      case Some(jsValue) =>
        jsValue.validate[PenaltiesCache] match {
          case JsSuccess(value, _) if value.psaId == psaId => Future.successful(value.penalties)
          case _ => saveAndReturnPenalties(psaId)
        }
      case _ => saveAndReturnPenalties(psaId)
    }

  //Navigation helper methods

  def navFromOverviewPage(penalties: Seq[PsaFS], psaId: String)
  (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Result] = {
    val penaltyTypes: Seq[PenaltyType] = penalties.map(p => getPenaltyType(p.chargeType)).distinct

    if (penaltyTypes.nonEmpty && penaltyTypes.size > 1) {
      Future.successful(Redirect(PenaltyTypeController.onPageLoad()))
    } else if (penaltyTypes.size == 1) {
      navFromPenaltiesTypePage(penalties, penaltyTypes.head, psaId)
    } else {
      Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
    }
  }

  def navFromPenaltiesTypePage(penalties: Seq[PsaFS], penaltyType: PenaltyType, psaId: String)
                              (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Result] = {

    val yearsSeq = penalties
      .filter(p => getPenaltyType(p.chargeType) == penaltyType)
      .map { penalty => penalty.periodEndDate.getYear }.distinct

    (penaltyType, yearsSeq.size) match {
      case (AccountingForTaxPenalties, 1) => navFromAftYearsPage(penalties, yearsSeq.head, psaId)
      case (_, 1) => navFromNonAftYearsPage(penalties, yearsSeq.head.toString, psaId, penaltyType)
      case (_, size) if size > 1 =>Future.successful(Redirect(SelectPenaltiesYearController.onPageLoad(penaltyType)))
      case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
    }

  }

  def navFromNonAftYearsPage(penalties: Seq[PsaFS], year: String, psaId: String, penaltyType: PenaltyType)
                            (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Result] = {

    val penaltiesUrl = penaltyType match {
      case ContractSettlementCharges => identifier => PenaltiesController.onPageLoadContract(year, identifier)
      case InformationNoticePenalties => identifier => PenaltiesController.onPageLoadInfoNotice(year, identifier)
      case _ => identifier => PenaltiesController.onPageLoadPension(year, identifier)
    }

    penaltySchemes(year.toInt, psaId, penaltyType, penalties).map { schemes =>
      if (schemes.size > 1) {
        Redirect(SelectSchemeController.onPageLoad(penaltyType, year))
      } else if (schemes.size == 1) {
        schemes.head.srn match {
          case Some(srn) =>
            Redirect(penaltiesUrl(srn))
          case _ =>
            val pstrIndex: String = penalties.map(_.pstr).indexOf(schemes.head.pstr).toString
            Redirect(penaltiesUrl(pstrIndex))
        }
      } else {
        Redirect(controllers.routes.SessionExpiredController.onPageLoad())
      }
    }
  }

  def navFromAftYearsPage(penalties: Seq[PsaFS], year: Int, psaId: String)
                         (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Result] = {
    val quartersSeq = penalties
      .filter(p => getPenaltyType(p.chargeType) == AccountingForTaxPenalties)
      .filter(_.periodStartDate.getYear == year)
      .map(_.periodStartDate).distinct

    if (quartersSeq.size > 1) {
      Future.successful(Redirect(SelectPenaltiesQuarterController.onPageLoad(year.toString)))
    } else if (quartersSeq.size == 1) {
      navFromAftQuartersPage(penalties, quartersSeq.head, psaId)
    } else {
      Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
    }
  }

  def navFromAftQuartersPage(penalties: Seq[PsaFS], startDate: LocalDate, psaId: String)
                            (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Result] =
    penaltySchemes(startDate, psaId, penalties).map { schemes =>
      if(schemes.size > 1) {
        Redirect(SelectSchemeController.onPageLoad(AccountingForTaxPenalties, startDate))
      } else if (schemes.size == 1) {
        schemes.head.srn match {
          case Some(srn) =>
            Redirect(PenaltiesController.onPageLoadAft(startDate, srn))
          case _ =>
            val pstrIndex: String = penalties.map(_.pstr).indexOf(schemes.head.pstr).toString
            Redirect(PenaltiesController.onPageLoadAft(startDate, pstrIndex))
        }
      } else {
        Redirect(controllers.routes.SessionExpiredController.onPageLoad())
      }
    }

  def getTypeParam(penaltyType: PenaltyType)(implicit messages: Messages): String =
    if(penaltyType == AccountingForTaxPenalties) {
      messages(s"penaltyType.${penaltyType.toString}")
    } else {
      messages(s"penaltyType.${penaltyType.toString}").toLowerCase()
    }

}

case class PenaltiesCache(psaId: String, penalties: Seq[PsaFS])
object PenaltiesCache {
  implicit val format: OFormat[PenaltiesCache] = Json.format[PenaltiesCache]
}
