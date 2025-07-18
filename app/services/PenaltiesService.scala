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

package services

import com.google.inject.Inject
import connectors.cache.FinancialInfoCacheConnector
import connectors.{FinancialStatementConnector, ListOfSchemesConnector, MinimalConnector}
import controllers.financialStatement.penalties.routes._
import helpers.FormatHelper
import models.LocalDateBinder._
import models.PenaltiesFilter.Outstanding
import models.financialStatement.PenaltyType._
import models.financialStatement.PsaFSChargeType.CONTRACT_SETTLEMENT
import models.financialStatement.{PenaltyType, PsaFSDetail}
import models.{ListSchemeDetails, PenaltiesFilter, PenaltySchemes}
import play.api.Logger
import play.api.i18n.Messages
import play.api.libs.json.{JsSuccess, Json, OFormat}
import play.api.mvc.Result
import play.api.mvc.Results.Redirect
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{Key, SummaryListRow, Value}
import uk.gov.hmrc.http.HeaderCarrier
import utils.DateHelper.dateFormatterDMY
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.{HtmlContent, Text}
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.{HeadCell, TableRow, Table}

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class PenaltiesService @Inject()(fsConnector: FinancialStatementConnector,
                                 fiCacheConnector: FinancialInfoCacheConnector,
                                 listOfSchemesConnector: ListOfSchemesConnector,
                                 minimalConnector: MinimalConnector) {

  private val logger = Logger(classOf[PenaltiesService])

  val isPaymentOverdue: PsaFSDetail => Boolean = data => data.amountDue > BigDecimal(0.00) && data.dueDate.exists(_.isBefore(LocalDate.now()))

  //PENALTIES
  def getPsaFsTable(penalties: Seq[PsaFSDetail], identifier: String, chargeRefsIndex: String => String, penaltyType: PenaltyType, journeyType: PenaltiesFilter)
                   (implicit messages: Messages): Table = {

    val head: Seq[HeadCell] = Seq(
      HeadCell(Text(messages(s"penalties.column.${if(penaltyType == ContractSettlementCharges) "chargeType" else "penaltyType"}"))),
      HeadCell(Text(messages("penalties.column.amount"))),
      HeadCell(Text(messages("penalties.column.chargeReference"))),
      HeadCell(HtmlContent(s"<span class='govuk-visually-hidden'>${messages("penalties.column.paymentStatus")}</span>"))
    )

    val rows = penalties.flatMap { data =>
      val content = chargeTypeLink(identifier, data, chargeRefsIndex(data.chargeReference), journeyType)
      val charge = Seq(
        TableRow(content, classes = "govuk-!-width-two-thirds-quarter"),
        TableRow(Text(s"${FormatHelper.formatCurrencyAmountAsString(data.amountDue)}"),
          classes = "govuk-!-width-one-quarter"),
        TableRow(Text(data.chargeReference), classes = "govuk-!-width-one-quarter"),
        statusCell(data)
      )

      val interest = if (data.chargeType == CONTRACT_SETTLEMENT && data.accruedInterestTotal > 0) {
        val content = accruedInterestLink(identifier, data, chargeRefsIndex(data.chargeReference))
        Seq(
          TableRow(content, classes = "govuk-!-width-two-thirds-quarter"),
          TableRow(Text(s"${FormatHelper.formatCurrencyAmountAsString(data.accruedInterestTotal)}"),
            classes = "govuk-!-width-one-quarter"),
          TableRow(Text(Messages("penalties.column.chargeReference.toBeAssigned")), classes = "govuk-!-width-one-quarter"),
          TableRow(HtmlContent(s"<span class='govuk-body govuk-tag govuk-tag--blue'>${messages("penalties.status.interestAccruing").toUpperCase}</span>"))
        )
      } else {
        Nil
      }

      (Seq(charge) ++ Seq(interest)).filterNot(_.isEmpty)
    }
    Table(head = Some(head), rows = rows, attributes = Map("role" -> "table"))
  }

  private def chargeTypeLink(identifier: String, data: PsaFSDetail, chargeRefsIndex: String, journeyType: PenaltiesFilter)
                            (implicit messages: Messages): HtmlContent =
    HtmlContent(s"<a id=${data.chargeReference} " +
            s"class=govuk-link href=${controllers.financialStatement.penalties.routes
              .ChargeDetailsController.onPageLoad(identifier, chargeRefsIndex, journeyType)}>" +
            s"${messages(data.chargeType.toString)}" +
            s"<span class=govuk-visually-hidden>${messages(s"penalties.visuallyHiddenText", data.chargeReference)}</span> </a>")

  private def accruedInterestLink(identifier: String, data: PsaFSDetail, chargeRefsIndex: String)
    (implicit messages: Messages): HtmlContent =
    HtmlContent(s"<a id=${data.chargeReference}-interest " +
      s"class=govuk-link href=${controllers.financialStatement.penalties.routes
        .InterestController.onPageLoad(identifier, chargeRefsIndex)}>" +
      s"${messages("penalties.column.chargeType.interestOn", messages(data.chargeType.toString.toLowerCase))}" +
      s"<span class=govuk-visually-hidden>${messages(s"penalties.column.chargeType.interestToCome")}</span> </a>")

  private def statusCell(data: PsaFSDetail)(implicit messages: Messages): TableRow = {
    val (classes, content) = (isPaymentOverdue(data), data.amountDue) match {
      case (true, _) => ("govuk-tag govuk-tag--red", messages("penalties.status.paymentOverdue"))
      case (_, amount) if amount == BigDecimal(0.00) =>
        ("govuk-visually-hidden", messages("penalties.status.visuallyHiddenText.noPaymentDue"))
      case _ => ("govuk-visually-hidden", messages("penalties.status.visuallyHiddenText.paymentIsDue"))
    }
    TableRow(HtmlContent(s"""<span class='$classes'>$content</span>"""))
  }

  //CHARGE DETAILS
  def chargeDetailsRows(data: PsaFSDetail)(implicit messages: Messages): Seq[SummaryListRow] =
    Seq(
      SummaryListRow(
        key = Key(Text(data.chargeType.toString), classes = "govuk-!-width-three-quarters"),
        value = Value(Text(s"${FormatHelper.formatCurrencyAmountAsString(data.totalAmount)}"),
          classes = "govuk-!-width-one-quarter govuk-table__cell--numeric")
      )
    ) ++ paymentRow(data) ++ amountUnderReviewRow(data) ++ totalDueRow(data)


  private def totalInterestDueRow(data: PsaFSDetail)(implicit messages: Messages): Seq[SummaryListRow] = {
    val dateAsOf: String = LocalDate.now.format(dateFormatterDMY)
    Seq(SummaryListRow(
      key = Key(Text(messages("penalties.interest.totalDueAsOf", dateAsOf)), classes = "govuk-table__header--numeric govuk-!-padding-right-0"),
      value = Value(Text(s"${FormatHelper.formatCurrencyAmountAsString(data.accruedInterestTotal)}"),
        classes = "govuk-!-width-one-quarter govuk-table__cell--numeric")
    ))
  }

  def interestRows(data: PsaFSDetail)(implicit messages: Messages): Seq[SummaryListRow] =
    Seq(
      SummaryListRow(
        key = Key(Text(messages("penalties.status.interestAccruing")), classes = "govuk-!-width-three-quarters"),
        value = Value(Text(s"${FormatHelper.formatCurrencyAmountAsString(data.accruedInterestTotal)}"),
          classes = "govuk-!-width-one-quarter govuk-table__cell--numeric")
      )
    ) ++ totalInterestDueRow(data)

  private def paymentRow(data: PsaFSDetail)(implicit messages: Messages): Seq[SummaryListRow] = {
    val paymentAmount: BigDecimal = data.totalAmount - data.outstandingAmount
    if (paymentAmount != BigDecimal(0.00)) {
      Seq(SummaryListRow(
        key = Key(Text(messages("penalties.chargeDetails.payments")), classes = "govuk-!-width-three-quarters"),
        value = Value(Text(s"${FormatHelper.formatCurrencyAmountAsString(data.totalAmount - data.amountDue - data.stoodOverAmount)}"),
          classes = "govuk-!-width-one-quarter govuk-table__cell--numeric")
      ))
    }
    else {
      Nil
    }
  }

  private def amountUnderReviewRow(data: PsaFSDetail)(implicit messages: Messages): Seq[SummaryListRow] = {
    if (data.stoodOverAmount != BigDecimal(0.00)) {
      Seq(SummaryListRow(
        key = Key(Text(messages("penalties.chargeDetails.amountUnderReview")), classes = "govuk-!-width-three-quarters"),
        value = Value(Text(s"${FormatHelper.formatCurrencyAmountAsString(data.stoodOverAmount)}"),
          classes = "govuk-!-width-one-quarter govuk-table__cell--numeric")
      ))
    }
    else {
      Nil
    }
  }

  private def totalDueRow(data: PsaFSDetail)(implicit messages: Messages): Seq[SummaryListRow] = {
    if (data.amountDue > BigDecimal(0.00) && data.dueDate.isDefined) {
      val dueDate: String = data.dueDate.get.format(dateFormatterDMY)
      Seq(SummaryListRow(
        key = Key(Text(messages("penalties.chargeDetails.totalDueBy", dueDate)), classes = "govuk-table__header--numeric govuk-!-padding-right-0"),
        value = Value(Text(s"${FormatHelper.formatCurrencyAmountAsString(data.amountDue)}"),
          classes = "govuk-!-width-one-quarter govuk-table__cell--numeric")
      ))
    }
    else {
      Seq(SummaryListRow(
        key = Key(Text(messages("penalties.chargeDetails.totalDue")), classes = "govuk-table__header--numeric govuk-!-padding-right-0"),
        value = Value(Text(s"${FormatHelper.formatCurrencyAmountAsString(data.amountDue)}"),
          classes = "govuk-!-width-one-quarter govuk-table__cell--numeric")
      ))
    }
  }

  //SELECT SCHEME
  def penaltySchemes(startDate: LocalDate, psaId: String, penalties: Seq[PsaFSDetail])
                    (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[PenaltySchemes]] = {

      val filteredPenalties = penalties
        .filter(p => getPenaltyType(p.chargeType) == AccountingForTaxPenalties)
        .filter(_.periodStartDate == startDate)

      penaltySchemes(filteredPenalties, psaId)
    }

  def penaltySchemes(year: Int, psaId: String, penaltyType: PenaltyType, penalties: Seq[PsaFSDetail])
                    (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[PenaltySchemes]] = {

      val filteredPenalties = penalties
        .filter(p => getPenaltyType(p.chargeType) == penaltyType)
        .filter(_.periodEndDate.getYear == year)

      penaltySchemes(filteredPenalties, psaId)
    }

  private def penaltySchemes(filteredPenalties: Seq[PsaFSDetail], psaId: String)
                    (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[PenaltySchemes]] =
    for {
      listOfSchemes <- getListOfSchemes(psaId)
    } yield {

      val penaltyPstrs: Seq[String] = filteredPenalties.map(_.pstr).distinct
      val schemesWithPstr: Seq[ListSchemeDetails] = listOfSchemes.filter(_.pstr.isDefined)

      val associatedSchemes: Seq[PenaltySchemes] = schemesWithPstr
        .filter(scheme => penaltyPstrs.contains(scheme.pstr.get))
        .map(x => PenaltySchemes(Some(x.name), x.pstr.get, Some(x.referenceNumber), None))

      val unassociatedSchemes: Seq[PenaltySchemes] = penaltyPstrs
        .filter(penaltyPstr => !schemesWithPstr.map(_.pstr.get).contains(penaltyPstr))
        .map(x => PenaltySchemes(None, x, None, None))

      associatedSchemes ++ unassociatedSchemes
    }

  def unassociatedSchemes(seqPsaFS: Seq[PsaFSDetail], startDate: LocalDate, psaId: String)
                         (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[PsaFSDetail]] = {
    for {
      listOfSchemes <- getListOfSchemes(psaId)
      schemesWithPstr = listOfSchemes.filter(_.pstr.isDefined)
    } yield
      seqPsaFS
        .filter(_.periodStartDate == startDate)
        .filter(psaFS => !schemesWithPstr.map(_.pstr.get).contains(psaFS.pstr))
  }

  def unassociatedSchemes(seqPsaFS: Seq[PsaFSDetail], year: Int, psaId: String)
                         (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[PsaFSDetail]] = {
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
  def saveAndReturnPenalties(psaId: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[PenaltiesCache] =
    for {
      penalties <- fsConnector.getPsaFS(psaId)
      minimalDetails <- minimalConnector.getMinimalPsaDetails()
      _ <- fiCacheConnector.save(Json.toJson(PenaltiesCache(psaId, minimalDetails.name, penalties.seqPsaFSDetail)))
    } yield PenaltiesCache(psaId, minimalDetails.name, penalties.seqPsaFSDetail)


  def getPenaltiesFromCache(psaId: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[PenaltiesCache] =
    fiCacheConnector.fetch flatMap {
      case Some(jsValue) =>
      jsValue.validate[PenaltiesCache] match {
          case JsSuccess(value, _) if value.psaId == psaId => Future.successful(value)
          case _ => saveAndReturnPenalties(psaId)
        }
      case _ => saveAndReturnPenalties(psaId)
    }

  def getPenaltiesForJourney(psaId: String, journeyType: PenaltiesFilter)
                           (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[PenaltiesCache] =
    getPenaltiesFromCache(psaId).map { cache =>
      journeyType match {
        case Outstanding => cache.copy(penalties = cache.penalties.filter(_.amountDue > BigDecimal(0.00)))
        case _ => cache
      }
    }
  //Navigation helper methods

  def navFromOverviewPage(penalties: Seq[PsaFSDetail], psaId: String, journeyType: PenaltiesFilter)
  (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Result] = {
    val penaltyTypes: Seq[PenaltyType] = penalties.map(p => getPenaltyType(p.chargeType)).distinct

    if (penaltyTypes.nonEmpty) {
      logger.debug(s"Skipping the penalty type page for type ${penaltyTypes.head}")
      navFromPenaltiesTypePage(penalties, penaltyTypes.head, psaId, journeyType)
    } else {
      Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

  def navFromPenaltiesTypePage(penalties: Seq[PsaFSDetail], penaltyType: PenaltyType, psaId: String, journeyType: PenaltiesFilter)
                              (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Result] = {

    val yearsSeq = penalties
      .filter(p => getPenaltyType(p.chargeType) == penaltyType)
      .map { penalty => penalty.periodEndDate.getYear }.distinct

    (penaltyType, yearsSeq.size) match {
      case (AccountingForTaxPenalties, 1) => navFromAftYearsPage(penalties, yearsSeq.head, psaId, journeyType)
      case (EventReportingCharges, 1) => Future.successful(Redirect(SelectPenaltiesYearController.onPageLoad(penaltyType, journeyType)))
      case (_, 1) => navFromNonAftYearsPage(penalties, yearsSeq.head.toString, psaId, penaltyType, journeyType)
      case (_, size) if size > 1 => Future.successful(Redirect(SelectPenaltiesYearController.onPageLoad(penaltyType, journeyType)))
      case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }

  }

  def navFromNonAftYearsPage(penalties: Seq[PsaFSDetail], year: String, psaId: String, penaltyType: PenaltyType, journeyType: PenaltiesFilter)
                            (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Result] = {

    val penaltiesUrl = penaltyType match {
      case ContractSettlementCharges => (identifier: String) => PenaltiesController.onPageLoadContract(year, identifier, journeyType)
      case InformationNoticePenalties => (identifier: String) => PenaltiesController.onPageLoadInfoNotice(year, identifier, journeyType)
      case _ => (identifier: String) => PenaltiesController.onPageLoadPension(year, identifier, journeyType)
    }

    penaltySchemes(year.toInt, psaId, penaltyType, penalties).map { schemes =>
      if (schemes.size > 1) {
        Redirect(SelectSchemeController.onPageLoad(penaltyType, year, journeyType))
      } else if (penaltyType == EventReportingCharges) {
        Redirect(SelectPenaltiesYearController.onPageLoad(penaltyType, journeyType))
      } else if (schemes.size == 1) {
        logger.debug(s"Skipping the select scheme page for year $year and type $penaltyType")
        schemes.head.srn match {
          case Some(srn) =>
            Redirect(penaltiesUrl(srn))
          case _ =>
            val pstrIndex: String = penalties.map(_.pstr).indexOf(schemes.head.pstr).toString
            Redirect(penaltiesUrl(pstrIndex))
        }
      } else {
        Redirect(controllers.routes.SessionExpiredController.onPageLoad)
      }
    }
  }

  def navFromERYearsPage(penalties: Seq[PsaFSDetail], year: Int, psaId: String, journeyType: PenaltiesFilter)
                         (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Result] = {
    /*TODO implement nav to select scheme page*/
    ???
  }

  def navFromAftYearsPage(penalties: Seq[PsaFSDetail], year: Int, psaId: String, journeyType: PenaltiesFilter)
                         (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Result] = {
    val quartersSeq = penalties
      .filter(p => getPenaltyType(p.chargeType) == AccountingForTaxPenalties)
      .filter(_.periodStartDate.getYear == year)
      .map(_.periodStartDate).distinct

    if (quartersSeq.size > 1) {
      Future.successful(Redirect(SelectPenaltiesQuarterController.onPageLoad(year.toString, journeyType)))
    } else if (quartersSeq.size == 1) {
      logger.debug(s"Skipping the select quarter page for year $year and type AFT")
      navFromAftQuartersPage(penalties, quartersSeq.head, psaId, journeyType)
    } else {
      Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

  def navFromAftQuartersPage(penalties: Seq[PsaFSDetail], startDate: LocalDate, psaId: String, journeyType: PenaltiesFilter)
                            (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Result] =
    penaltySchemes(startDate, psaId, penalties).map { schemes =>
      if(schemes.size > 1) {
        Redirect(SelectSchemeController.onPageLoad(AccountingForTaxPenalties, startDate, journeyType))
      } else if (schemes.size == 1) {
        logger.debug(s"Skipping the select scheme page for startDate $startDate and type AFT")
        schemes.head.srn match {
          case Some(srn) =>
            Redirect(PenaltiesController.onPageLoadAft(startDate, srn, journeyType))
          case _ =>
            val pstrIndex: String = penalties.map(_.pstr).indexOf(schemes.head.pstr).toString
            Redirect(PenaltiesController.onPageLoadAft(startDate, pstrIndex, journeyType))
        }
      } else {
        Redirect(controllers.routes.SessionExpiredController.onPageLoad)
      }
    }

  def getTypeParam(penaltyType: PenaltyType)(implicit messages: Messages): String =
    if(penaltyType == AccountingForTaxPenalties) {
      messages(s"penaltyType.${penaltyType.toString}")
    } else {
      messages(s"penaltyType.${penaltyType.toString}").toLowerCase()
    }

}

case class PenaltiesCache(psaId: String, psaName: String, penalties: Seq[PsaFSDetail])
object PenaltiesCache {
  implicit val format: OFormat[PenaltiesCache] = Json.format[PenaltiesCache]
}
