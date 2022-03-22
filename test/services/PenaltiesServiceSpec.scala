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

package services

import base.SpecBase
import config.FrontendAppConfig
import connectors.MinimalConnector.MinimalDetails
import connectors.cache.FinancialInfoCacheConnector
import connectors.{FinancialStatementConnector, ListOfSchemesConnector, MinimalConnector}
import controllers.financialStatement.penalties.routes._
import data.SampleData.{paymentsCache, psaFs, psaFsSeq, psaId, schemeFSResponseAftAndOTC}
import helpers.FormatHelper
import models.LocalDateBinder._
import models.PenaltiesFilter.All
import models.financialStatement.PenaltyType.{AccountingForTaxPenalties, ContractSettlementCharges, InformationNoticePenalties, PensionsPenalties}
import models.financialStatement.PsaFSChargeType._
import models.financialStatement.{DocumentLineItemDetail, FSClearingReason, PsaFS, PsaFSChargeType, PsaFSDetail}
import models.{ListOfSchemes, ListSchemeDetails, PenaltiesFilter, PenaltySchemes}
import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.i18n.Messages
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Results
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Table.Cell
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Html, _}
import utils.DateHelper
import utils.DateHelper.dateFormatterDMY

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PenaltiesServiceSpec extends SpecBase with ScalaFutures with BeforeAndAfterEach with MockitoSugar with Results {

  import PenaltiesServiceSpec._

  private val mockAppConfig: FrontendAppConfig = mock[FrontendAppConfig]
  private val mockFSConnector: FinancialStatementConnector = mock[FinancialStatementConnector]
  private val mockFIConnector: FinancialInfoCacheConnector = mock[FinancialInfoCacheConnector]
  private val mockListOfSchemesConn: ListOfSchemesConnector = mock[ListOfSchemesConnector]
  private val mockMinimalConnector: MinimalConnector = mock[MinimalConnector]
  private val penaltiesService = new PenaltiesService(mockFSConnector, mockFIConnector, mockListOfSchemesConn, mockMinimalConnector)

  def penaltyTables(
    rows: Seq[Seq[Cell]],
    head: Seq[Cell] = headForChargeType("penalties.column.penaltyType")
  ): JsObject = {
    Json.obj(
      "penaltyTable" -> Table(head = head, rows = rows,
        attributes = Map("role" -> "table"))
    )
  }

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockListOfSchemesConn)
    when(mockAppConfig.minimumYear).thenReturn(year)
    when(mockMinimalConnector.getMinimalPsaDetails(any())(any(), any()))
      .thenReturn(Future(MinimalDetails("", isPsaSuspended = false, Some("psa-name"), None, rlsFlag = false, deceasedFlag = false)))
    when(mockFIConnector.fetch(any(), any())).thenReturn(Future.successful(Some(Json.toJson(psaFSResponse()))))
    when(mockListOfSchemesConn.getListOfSchemes(any())(any(), any())).thenReturn(Future.successful(Right(listOfSchemes)))
    DateHelper.setDate(Some(dateNow))
  }

  "getPsaFsJson" must {

    "return the penalty tables based on API response for contract settlement where there is interestAccruedTotal" in {
      val expectedRows = Seq(
        penaltiesRow(
          link = contractSettlementLink(),
          statusClass = "govuk-visually-hidden",
          statusMessageKey = "penalties.status.visuallyHiddenText.paymentIsDue",
          amountDue = "500.23",
          chargeRef = "XY002610150184"),
        penaltiesRow(
          link = interestOnContractSettlementLink(),
          statusClass = "govuk-body govuk-tag govuk-tag--blue",
          statusMessageKey = Messages("penalties.status.interestAccruing").toUpperCase,
          amountDue = "123.45",
          chargeRef = "To be assigned")
      )

      val charge = createPsaFS(
        amountDue = BigDecimal(500.23),
        accruedInterestTotal = BigDecimal(123.45), chargeType = CONTRACT_SETTLEMENT
      )

      penaltiesService.getPsaFsJson(
        Seq(charge),
        srn, chargeRefIndex, ContractSettlementCharges, PenaltiesFilter.All
      ) mustBe
        penaltyTables(
          rows = expectedRows,
          head = headForChargeType(firstColumnMessageKey = "penalties.column.chargeType")
        )
    }

    "return the penalty tables based on API response for paymentOverdue" in {
      penaltiesService.getPsaFsJson(psaFSResponse(amountDue = 1029.05, dueDate = LocalDate.parse("2020-07-15")),
        srn, chargeRefIndex, AccountingForTaxPenalties, All) mustBe
          penaltyTables(
            rows = rows(
              link = aftLink(),
              statusClass = "govuk-tag govuk-tag--red",
              statusMessageKey = "penalties.status.paymentOverdue",
              amountDue = "1,029.05"
            )
          )
    }

    "return the penalty tables based on API response for noPaymentDue" in {
      penaltiesService.getPsaFsJson(
        psaFSResponse(amountDue = 0.00, dueDate = LocalDate.parse("2020-07-15")), srn, chargeRefIndex, AccountingForTaxPenalties, All) mustBe
          penaltyTables(
            rows(
              link = aftLink(),
              statusClass = "govuk-visually-hidden",
              statusMessageKey = "penalties.status.visuallyHiddenText.noPaymentDue",
              amountDue = "0.00"
            )
          )
    }

    "return the penalty tables based on API response for paymentIsDue" in {
      penaltiesService.getPsaFsJson(psaFSResponse(amountDue = 5.00, dueDate = LocalDate.now()), srn, chargeRefIndex, AccountingForTaxPenalties, All) mustBe
          penaltyTables(
            rows(
              link = aftLink(),
              statusClass = "govuk-visually-hidden",
              statusMessageKey = "penalties.status.visuallyHiddenText.paymentIsDue",
              amountDue = "5.00"
            )
          )
      }
  }

  "isPaymentOverdue" must {
    "return true if amountDue is greater than 0 and due date is after today" in {
      penaltiesService.isPaymentOverdue(psaFS(BigDecimal(0.01), Some(dateNow.minusDays(1)))) mustBe true
    }

    "return false if amountDue is less than or equal to 0" in {
      penaltiesService.isPaymentOverdue(psaFS(BigDecimal(0.00), Some(dateNow.minusDays(1)))) mustBe false
    }

    "return false if amountDue is greater than 0 and due date is not defined" in {
      penaltiesService.isPaymentOverdue(psaFS(BigDecimal(0.01), None)) mustBe false
    }

    "return false if amountDue is greater than 0 and due date is today" in {
      penaltiesService.isPaymentOverdue(psaFS(BigDecimal(0.01), Some(dateNow))) mustBe false
    }
  }

  "chargeDetailsRows" must {
    "return the correct rows when all rows need to be displayed" in {
      val rows = Seq(totalAmount(), paymentAmount(), reviewAmount(), totalDueAmount(date = "15 July 2020"))
      penaltiesService.chargeDetailsRows(psaFSResponse(amountDue = 1029.05, dueDate = LocalDate.parse("2020-07-15")).head) mustBe rows
    }

    "return the correct rows when payment row need not be displayed" in {
      val rows = Seq(totalAmount(), reviewAmount(), totalDueAmount())
      val psaData = psaFS(outStandingAmount = BigDecimal(80000.00))
      penaltiesService.chargeDetailsRows(psaData) mustBe rows
    }

    "return the correct rows when amountUnderReview row need not be displayed" in {
      val rows = Seq(totalAmount(), paymentAmount(BigDecimal(78970.95)), totalDueAmount())
      val psaData = psaFS(stoodOverAmount = BigDecimal(0.00))
      penaltiesService.chargeDetailsRows(psaData) mustBe rows
    }

    "return the correct rows when totalDue row need is displayed without date" in {
      val rows = Seq(totalAmount(), paymentAmount(), reviewAmount(), totalDueAmountWithoutDate())
      val psaData = psaFS(dueDate = None)
      penaltiesService.chargeDetailsRows(psaData) mustBe rows
    }
  }

  "penaltySchemes" must {
    "return a combination of all associated and unassociated schemes returned in correct format" in {
      when(mockFIConnector.fetch(any(), any())) thenReturn Future.successful(Some(Json.toJson(PenaltiesCache("PsaID", "psa-name", psaFSResponse()))))

      whenReady(penaltiesService.penaltySchemes("2020-04-01", "PsaID", psaFSResponse())(implicitly, implicitly)) {
        _ mustBe penaltySchemes
      }
    }
  }

  "isPaymentOverdue" must {
    "return true if the amount due is positive and due date is before today" in {
      val psaFS: PsaFSDetail = psaFsSeq.head
      penaltiesService.isPaymentOverdue(psaFS) mustBe true
    }

    "return false if the amount due is negative and due date is before today" in {
      val psaFS: PsaFSDetail = psaFsSeq.head.copy(amountDue = BigDecimal(0.00))
      penaltiesService.isPaymentOverdue(psaFS) mustBe false
    }

    "return true if the amount due is positive and due date is today" in {
      val psaFS: PsaFSDetail = psaFsSeq.head.copy(dueDate = Some(LocalDate.now()))
      penaltiesService.isPaymentOverdue(psaFS) mustBe false
    }

    "return true if the amount due is positive and due date is none" in {
      val psaFS: PsaFSDetail = psaFsSeq.head.copy(dueDate = None)
      penaltiesService.isPaymentOverdue(psaFS) mustBe false
    }
  }

  "getPenaltiesFromCache" must {
    "return payload from cache is srn and logged in id match the payload" in {
      when(mockFIConnector.fetch(any(), any()))
        .thenReturn(Future.successful(Some(Json.toJson(penaltiesCache))))
      whenReady(penaltiesService.getPenaltiesForJourney(psaId, All)){ _ mustBe PenaltiesCache(psaId, "psa-name", psaFsSeq) }
    }

    "call FS API and save to cache if logged in id does not match the retrieved payload from cache" in {
      when(mockFIConnector.fetch(any(), any())).thenReturn(Future.successful(Some(Json.toJson(penaltiesCache.copy(psaId = "wrong-id")))))
      when(mockFSConnector.getPsaFS(any())(any(), any())).thenReturn(Future.successful(psaFSResponseStub))
      when(mockFIConnector.save(any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      whenReady(penaltiesService.getPenaltiesForJourney(psaId, All)){ _ mustBe PenaltiesCache(psaId, "psa-name", psaFSResponse()) }
    }

    "call FS API and save to cache if retrieved payload from cache is not in Payments format" in {
      when(mockFIConnector.fetch(any(), any())).thenReturn(Future.successful(Some(Json.toJson(paymentsCache(schemeFSResponseAftAndOTC.seqSchemeFSDetail)))))
      when(mockFSConnector.getPsaFS(any())(any(), any())).thenReturn(Future.successful(psaFs))
      when(mockFIConnector.save(any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      whenReady(penaltiesService.getPenaltiesForJourney(psaId, All)){ _ mustBe PenaltiesCache(psaId, "psa-name", psaFsSeq) }
    }

    "call FS API and save to cache if there is no existing payload stored in cache" in {
      when(mockFIConnector.fetch(any(), any())).thenReturn(Future.successful(None))
      when(mockFSConnector.getPsaFS(any())(any(), any())).thenReturn(Future.successful(psaFs))
      when(mockFIConnector.save(any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      whenReady(penaltiesService.getPenaltiesForJourney(psaId, All)){ _ mustBe PenaltiesCache(psaId, "psa-name", psaFsSeq) }
    }
  }

  "navFromAftQuartersPage" must {
    "redirect to SelectSchemePage if charges from multiple schemes are returned for this quarter" in {
      val apiResponse: Seq[PsaFSDetail] = Seq(customPsaFS(AFT_INITIAL_LFP), customPsaFS(OTC_6_MONTH_LPP, pstr = "24000041IN"))
      whenReady(penaltiesService.navFromAftQuartersPage(apiResponse, LocalDate.parse("2021-01-01"), psaId, All)){
        _ mustBe Redirect(SelectSchemeController.onPageLoad(AccountingForTaxPenalties, "2021-01-01", All))
      }
    }

    "redirect to PenaltiesController with srn if charges from single scheme is returned and pstr is found in list of schemes" in {
      val apiResponse: Seq[PsaFSDetail] = Seq(customPsaFS(AFT_INITIAL_LFP), customPsaFS(OTC_6_MONTH_LPP))
      val listOfSchemes: ListOfSchemes = ListOfSchemes("", "1", Some(List(ListSchemeDetails("scheme-name", srn, "", None, Some(pstr), None, None, None))))

      when(mockListOfSchemesConn.getListOfSchemes(any())(any(), any())).thenReturn(Future(Right(listOfSchemes)))

      whenReady(penaltiesService.navFromAftQuartersPage(apiResponse, LocalDate.parse("2021-01-01"), psaId, All)){
        _ mustBe Redirect(PenaltiesController.onPageLoadAft("2021-01-01", srn, All))
      }
    }

    "redirect to PenaltiesController with pstr index identifier if charges from single scheme is returned and pstr is not found in list of schemes" in {
      val apiResponse: Seq[PsaFSDetail] = Seq(customPsaFS(AFT_INITIAL_LFP), customPsaFS(OTC_6_MONTH_LPP))
      val listOfSchemes: ListOfSchemes = ListOfSchemes("", "1",
        Some(List(ListSchemeDetails("scheme-name", srn, "", None, Some("24000041IN"), None, None, None))))

      when(mockListOfSchemesConn.getListOfSchemes(any())(any(), any())).thenReturn(Future(Right(listOfSchemes)))

      whenReady(penaltiesService.navFromAftQuartersPage(apiResponse, LocalDate.parse("2021-01-01"), psaId, All)){
        _ mustBe Redirect(PenaltiesController.onPageLoadAft("2021-01-01", "0", All))
      }
    }
  }

  "navFromNonAftYearsPage" must {
    "redirect to SelectSchemePage if charges from multiple schemes are returned for the selected year" in {
      val apiResponse: Seq[PsaFSDetail] = Seq(customPsaFS(CONTRACT_SETTLEMENT), customPsaFS(CONTRACT_SETTLEMENT_INTEREST, pstr = "24000041IN"))
      whenReady(penaltiesService.navFromNonAftYearsPage(apiResponse, "2021", psaId, ContractSettlementCharges, All)){
        _ mustBe Redirect(SelectSchemeController.onPageLoad(ContractSettlementCharges, "2021", All))
      }
    }

    "redirect to PenaltiesController with srn if charges from single scheme is returned and pstr is found in list of schemes" in {
      val apiResponse: Seq[PsaFSDetail] = Seq(customPsaFS(PSS_PENALTY), customPsaFS(PSS_PENALTY, "2021-04-01", "2021-06-30"))
      val listOfSchemes: ListOfSchemes = ListOfSchemes("", "1", Some(List(ListSchemeDetails("scheme-name", srn, "", None, Some(pstr), None, None, None))))

      when(mockListOfSchemesConn.getListOfSchemes(any())(any(), any())).thenReturn(Future(Right(listOfSchemes)))

      whenReady(penaltiesService.navFromNonAftYearsPage(apiResponse, "2021", psaId, PensionsPenalties, All)){
        _ mustBe Redirect(PenaltiesController.onPageLoadPension("2021", srn, All))
      }
    }

    "redirect to PenaltiesController with pstr index identifier if charges from single scheme is returned and pstr is not found in list of schemes" in {
      val apiResponse: Seq[PsaFSDetail] = Seq(customPsaFS(PSS_INFO_NOTICE))
      val listOfSchemes: ListOfSchemes = ListOfSchemes("", "1",
        Some(List(ListSchemeDetails("scheme-name", srn, "", None, Some("24000041IN"), None, None, None))))

      when(mockListOfSchemesConn.getListOfSchemes(any())(any(), any())).thenReturn(Future(Right(listOfSchemes)))

      whenReady(penaltiesService.navFromNonAftYearsPage(apiResponse, "2021", psaId, InformationNoticePenalties, All)){
        _ mustBe Redirect(PenaltiesController.onPageLoadInfoNotice("2021", "0", All))
      }
    }
  }

  "navFromAftYearsPage" must {
    "redirect to SelectQuartersPage if charges from multiple quarters are returned for the selected year" in {
      val apiResponse: Seq[PsaFSDetail] = Seq(customPsaFS(AFT_INITIAL_LFP), customPsaFS(OTC_6_MONTH_LPP, startDate = "2021-04-01"))
      whenReady(penaltiesService.navFromAftYearsPage(apiResponse, 2021, psaId, All)){
        _ mustBe Redirect(SelectPenaltiesQuarterController.onPageLoad("2021", All))
      }
    }

    "redirect to page returned if single quarter is returned for the selected year" in {
      val apiResponse: Seq[PsaFSDetail] = Seq(customPsaFS(AFT_INITIAL_LFP), customPsaFS(OTC_6_MONTH_LPP))

      whenReady(penaltiesService.navFromAftYearsPage(apiResponse, 2021, psaId, All)){
        _ mustBe Redirect(PenaltiesController.onPageLoadAft("2021-01-01", "SRN123", All))
      }
    }

    "redirect to SessionExpired if no charges are returned for given year" in {
      val apiResponse: Seq[PsaFSDetail] = Seq(customPsaFS(AFT_INITIAL_LFP), customPsaFS(OTC_6_MONTH_LPP))

      whenReady(penaltiesService.navFromAftYearsPage(apiResponse, 2020, psaId, All)){
        _ mustBe Redirect(controllers.routes.SessionExpiredController.onPageLoad)
      }
    }
  }

  "navFromPenaltiesTypePage" must {
    "redirect to SelectYear page if API returns multiple years for AFT" in {
      val apiResponse: Seq[PsaFSDetail] = Seq(customPsaFS(AFT_INITIAL_LFP), customPsaFS(OTC_12_MONTH_LPP, "2020-01-01", "2020-03-31"))
      whenReady(penaltiesService.navFromPenaltiesTypePage(apiResponse, AccountingForTaxPenalties, psaId, All)){
        _ mustBe Redirect(SelectPenaltiesYearController.onPageLoad(AccountingForTaxPenalties, All))
      }
    }

    "redirect to SelectYear page if API returns multiple years for NON-AFT" in {
      val apiResponse: Seq[PsaFSDetail] = Seq(customPsaFS(PSS_INFO_NOTICE), customPsaFS(PSS_INFO_NOTICE, "2020-01-01", "2020-03-31"))
      whenReady(penaltiesService.navFromPenaltiesTypePage(apiResponse, InformationNoticePenalties, psaId, All)){
        _ mustBe Redirect(SelectPenaltiesYearController.onPageLoad(InformationNoticePenalties, All))
      }
    }

    "redirect to penaltyType page if API returns charges from a single year for AFT" in {
      val apiResponse: Seq[PsaFSDetail] = Seq(customPsaFS(AFT_INITIAL_LFP), customPsaFS(OTC_12_MONTH_LPP))
      whenReady(penaltiesService.navFromPenaltiesTypePage(apiResponse, AccountingForTaxPenalties, psaId, All)){
        _ mustBe Redirect(PenaltiesController.onPageLoadAft("2021-01-01", "SRN123", All))
      }
    }

    "redirect to penaltyType page if API returns charges from a single year for NON-AFT" in {
      val apiResponse: Seq[PsaFSDetail] = Seq(customPsaFS(CONTRACT_SETTLEMENT_INTEREST), customPsaFS(CONTRACT_SETTLEMENT))
      whenReady(penaltiesService.navFromPenaltiesTypePage(apiResponse, ContractSettlementCharges, psaId, All)){
        _ mustBe Redirect(PenaltiesController.onPageLoadContract("2021", "SRN123", All))
      }
    }

    "redirect to SessionExpired if no charges are returned for given year" in {
      val apiResponse: Seq[PsaFSDetail] = Seq(customPsaFS(CONTRACT_SETTLEMENT_INTEREST), customPsaFS(CONTRACT_SETTLEMENT))
      whenReady(penaltiesService.navFromPenaltiesTypePage(apiResponse, PensionsPenalties, psaId, All)){
        _ mustBe Redirect(controllers.routes.SessionExpiredController.onPageLoad)
      }
    }
  }

  "navFromOverviewPage" must {
    "redirect to penaltyType page if API returns multiple categories" in {
      val apiResponse: Seq[PsaFSDetail] = Seq(customPsaFS(AFT_INITIAL_LFP), customPsaFS(PSS_PENALTY))
      whenReady(penaltiesService.navFromOverviewPage(apiResponse, psaId, All)){ _ mustBe Redirect(PenaltyTypeController.onPageLoad(All))}
    }

    "redirect to penaltyType page if API returns charges in a single category" in {
      val apiResponse: Seq[PsaFSDetail] = Seq(customPsaFS(CONTRACT_SETTLEMENT_INTEREST), customPsaFS(CONTRACT_SETTLEMENT))
      whenReady(penaltiesService.navFromOverviewPage(apiResponse, psaId, All)){
        _ mustBe Redirect(PenaltiesController.onPageLoadContract("2021", "SRN123", All))
      }
    }

    "redirect to SessionExpired if no charges are returned for given year" in {
           whenReady(penaltiesService.navFromOverviewPage(Nil, psaId, All)){
        _ mustBe Redirect(controllers.routes.SessionExpiredController.onPageLoad)
      }
    }
  }

}

object PenaltiesServiceSpec {

  val year: Int = 2020
  val startDate: String = "2020-04-01"
  val srn: String = "S2400000041"
  val dateNow: LocalDate = LocalDate.now()
  val chargeRefIndex: String => String = _ => "0"

  val penaltiesCache: PenaltiesCache = PenaltiesCache(psaId, "psa-name", psaFsSeq)

  def createPsaFS(
    amountDue: BigDecimal = BigDecimal(0.01),
    dueDate: LocalDate = dateNow,
    chargeType: PsaFSChargeType = AFT_INITIAL_LFP,
    accruedInterestTotal: BigDecimal = BigDecimal(0.00)
  ): PsaFSDetail = {
    PsaFSDetail(
        chargeReference = "XY002610150184",
        chargeType = chargeType ,
        dueDate = Some(dueDate),
        totalAmount = 80000.00,
        outstandingAmount = 56049.08,
        stoodOverAmount = 25089.08,
        accruedInterestTotal = accruedInterestTotal,
        amountDue = amountDue,
        periodStartDate = LocalDate.parse("2020-04-01"),
        periodEndDate = LocalDate.parse("2020-06-30"),
        pstr = "24000040IN",
        sourceChargeRefForInterest = None,
        documentLineItemDetails = Nil
      )
  }
  val psaFSResponseStub: PsaFS = PsaFS(false, psaFSResponse())
  def psaFSResponse(amountDue: BigDecimal = BigDecimal(0.01), dueDate: LocalDate = dateNow): Seq[PsaFSDetail] = Seq(
    PsaFSDetail(
      chargeReference = "XY002610150184",
      chargeType = AFT_INITIAL_LFP,
      dueDate = Some(dueDate),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 0.00,
      amountDue = amountDue,
      periodStartDate = LocalDate.parse("2020-04-01"),
      periodEndDate = LocalDate.parse("2020-06-30"),
      pstr = "24000040IN",
      sourceChargeRefForInterest = None,
      documentLineItemDetails = Nil
    ),
    PsaFSDetail(
      chargeReference = "XY002610150185",
      chargeType = OTC_6_MONTH_LPP,
      dueDate = Some(dueDate),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 0.00,
      amountDue = amountDue,
      periodStartDate = LocalDate.parse("2020-04-01"),
      periodEndDate = LocalDate.parse("2020-06-30"),
      pstr = "24000041IN",
      sourceChargeRefForInterest = None,
      documentLineItemDetails = Nil
    ),
    PsaFSDetail(
      chargeReference = "XY002610150186",
      chargeType = OTC_6_MONTH_LPP,
      dueDate = Some(dueDate),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 0.00,
      amountDue = amountDue,
      periodStartDate = LocalDate.parse("2020-10-01"),
      periodEndDate = LocalDate.parse("2020-12-31"),
      pstr = "24000041IN",
      sourceChargeRefForInterest = None,
      documentLineItemDetails = Nil
    )
  )

  def psaFS(
             amountDue: BigDecimal = BigDecimal(1029.05),
             dueDate: Option[LocalDate] = Some(dateNow),
             totalAmount: BigDecimal = BigDecimal(80000.00),
             outStandingAmount: BigDecimal = BigDecimal(56049.08),
             stoodOverAmount: BigDecimal = BigDecimal(25089.08)
           ): PsaFSDetail =
    PsaFSDetail("XY002610150184", AFT_INITIAL_LFP, dueDate, totalAmount, amountDue, outStandingAmount, stoodOverAmount,
      accruedInterestTotal = 0.00, dateNow, dateNow, pstr, None, documentLineItemDetails = Seq(DocumentLineItemDetail(
        clearingReason= Some(FSClearingReason.CLEARED_WITH_PAYMENT),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        clearedAmountItem = BigDecimal(0.00))))


  val pstr: String = "24000040IN"
  val zeroAmount: BigDecimal = BigDecimal(0.00)
  val formattedDateNow: String = dateNow.format(dateFormatterDMY)

  private def headForChargeType(firstColumnMessageKey: String)(implicit messages: Messages) = Seq(
    Cell(msg"$firstColumnMessageKey"),
    Cell(msg"penalties.column.amount"),
    Cell(msg"penalties.column.chargeReference"),
    Cell(Html(s"<span class='govuk-visually-hidden'>${messages("penalties.column.paymentStatus")}</span>"))
  )

  private def penaltiesRow(link: Html,
    statusClass: String,
    statusMessageKey: String,
    amountDue: String,
    chargeRef: String
  )(implicit messages: Messages) = Seq(
    Cell(link, classes = Seq("govuk-!-width-two-thirds-quarter")),
    Cell(Literal(s"£$amountDue"), classes = Seq("govuk-!-width-one-quarter")),
    Cell(Literal(chargeRef), classes = Seq("govuk-!-width-one-quarter")),
    Cell(Html(s"<span class='$statusClass'>${messages(statusMessageKey)}</span>"))
  )

  private def rows(link: Html,
                   statusClass: String,
                   statusMessageKey: String,
                   amountDue: String,
                   link2: Html = otcLink()
                  )(implicit messages: Messages) = Seq(
      penaltiesRow(link, statusClass, statusMessageKey, amountDue, "XY002610150184"),
      penaltiesRow(link2, statusClass, statusMessageKey, amountDue, "XY002610150185"),
      penaltiesRow(otcLink("XY002610150186"), statusClass, statusMessageKey, amountDue, "XY002610150186")
    )

  def aftLink(chargeReference: String = "XY002610150184"): Html = Html(
    s"<a id=$chargeReference class=govuk-link " +
      s"href=${controllers.financialStatement.penalties.routes.ChargeDetailsController.onPageLoad(srn, "0", All).url}>" +
      s"Accounting for Tax late filing penalty<span class=govuk-visually-hidden>for charge reference $chargeReference</span> </a>")

  def contractSettlementLink(chargeReference: String = "XY002610150184"): Html = Html(
    s"<a id=$chargeReference class=govuk-link " +
      s"href=${controllers.financialStatement.penalties.routes.ChargeDetailsController.onPageLoad(srn, "0", PenaltiesFilter.All).url}>" +
      s"Contract settlement charge<span class=govuk-visually-hidden>for charge reference $chargeReference</span> </a>")

  def interestOnContractSettlementLink(chargeReference: String = "XY002610150184"): Html = Html(
    s"<a id=$chargeReference-interest class=govuk-link " +
      s"href=${controllers.financialStatement.penalties.routes.InterestController.onPageLoad(srn, "0").url}>" +
      s"Interest on contract settlement charge<span class=govuk-visually-hidden>for charge reference to come</span> </a>")

  def otcLink(chargeReference: String = "XY002610150185"): Html = Html(
    s"<a id=$chargeReference class=govuk-link " +
      s"href=${controllers.financialStatement.penalties.routes.ChargeDetailsController.onPageLoad(srn, "0", All).url}>" +
      s"Overseas transfer charge late payment penalty (6 months)<span class=govuk-visually-hidden>for charge reference $chargeReference</span> </a>")


  def totalAmount(amount: BigDecimal = BigDecimal(80000.00)): Row = Row(
    key = Key(Literal("Accounting for Tax late filing penalty"), classes = Seq("govuk-!-width-three-quarters")),
    value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(amount)}"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
  )

  def paymentAmount(amount: BigDecimal = BigDecimal(53881.87)): Row = Row(
    key = Key(msg"penalties.chargeDetails.payments", classes = Seq("govuk-!-width-three-quarters")),
    value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(amount)}"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
  )

  def reviewAmount(amount: BigDecimal = BigDecimal(25089.08)): Row = Row(
    key = Key(msg"penalties.chargeDetails.amountUnderReview", classes = Seq("govuk-!-width-three-quarters")),
    value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(amount)}"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
  )

  def totalDueAmount(amount: BigDecimal = BigDecimal(1029.05), date: String = formattedDateNow): Row = Row(
    key = Key(msg"penalties.chargeDetails.totalDueBy".withArgs(date), classes = Seq("govuk-table__header--numeric", "govuk-!-padding-right-0")),
    value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(amount)}"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
  )

  def totalDueAmountWithoutDate(amount: BigDecimal = BigDecimal(1029.05)): Row = Row(
    key = Key(msg"penalties.chargeDetails.totalDue", classes = Seq("govuk-table__header--numeric", "govuk-!-padding-right-0")),
    value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(amount)}"),
      classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
  )

  val penaltySchemes: Seq[PenaltySchemes] = Seq(
    PenaltySchemes(Some("Assoc scheme"), "24000040IN", Some("SRN123")),
    PenaltySchemes(None, "24000041IN", None)
  )

  val listOfSchemes: ListOfSchemes = ListOfSchemes("", "", Some(List(
    ListSchemeDetails("Assoc scheme", "SRN123", "", None, Some("24000040IN"), None, None))))

  def customPsaFS(chargeType: PsaFSChargeType, startDate: String = "2021-01-01", endDate: String = "2021-03-31", pstr: String = pstr): PsaFSDetail =
    PsaFSDetail("XY002610150184", chargeType, Some(LocalDate.parse("2021-05-15")), BigDecimal(0.00), BigDecimal(0.00), BigDecimal(0.00),
      BigDecimal(0.00), BigDecimal(0.00), LocalDate.parse(startDate), LocalDate.parse(endDate), pstr, Some(""), Nil)

}
