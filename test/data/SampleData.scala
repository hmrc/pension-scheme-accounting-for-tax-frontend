/*
 * Copyright 2023 HM Revenue & Customs
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

package data

import models.SponsoringEmployerType.{SponsoringEmployerTypeIndividual, SponsoringEmployerTypeOrganisation}
import models.chargeB.ChargeBDetails
import models.chargeC.{ChargeCDetails, SponsoringEmployerAddress, SponsoringOrganisationDetails}
import models.chargeD.ChargeDDetails
import models.chargeE.ChargeEDetails
import models.chargeG.{ChargeAmounts, MemberDetails => MemberDetailsG}
import models.financialStatement.PsaFSChargeType.{CONTRACT_SETTLEMENT_INTEREST, OTC_6_MONTH_LPP}
import models.financialStatement.SchemeFSChargeType.{PSS_AFT_RETURN, PSS_OTC_AFT_RETURN}
import models.financialStatement._
import models._
import pages.chargeC._
import pages.chargeD.{ChargeDetailsPage => ChargeDDetailsPage, MemberDetailsPage => ChargeDMemberDetailsPAge}
import pages.chargeE.{ChargeDetailsPage, MemberDetailsPage}
import play.api.libs.json.Json
import play.api.mvc.Call
import services.paymentsAndCharges.PaymentsCache
import utils.AFTConstants._
import viewmodels.Table

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object SampleData {
  //scalastyle.off: magic.number
  val userAnswersId = "id"
  val psaId = "A0000000"
  val pspId = "20000000"
  val srn = "aa"
  val submittedDate = "2016-12-17"
  val startDate = QUARTER_START_DATE
  val accessType = Draft
  val pstr = "pstr"
  val schemeName = "Big Scheme"
  val schemeIndex = 0
  val companyName = "Big Company"
  val crn = "AB121212"
  val dummyCall: Call = Call("GET", "/foo")
  val chargeAmount1 = BigDecimal(33.44)
  val chargeAmount2 = BigDecimal(50.00)
  val chargeAmount3 = BigDecimal(83.44)
  val chargeAmounts = ChargeAmounts(chargeAmount1, chargeAmount2)
  val chargeAmounts2 = ChargeAmounts(chargeAmount1, chargeAmount2)
  val chargeFChargeDetails = models.chargeF.ChargeDetails(LocalDate.of(2020, 4, 3), BigDecimal(33.44))
  val chargeAChargeDetails = models.chargeA.ChargeDetails(44, Some(chargeAmount1), Some(BigDecimal(34.34)), BigDecimal(67.78))
  val chargeEDetails = ChargeEDetails(chargeAmount1, LocalDate.of(2019, 4, 3), isPaymentMandatory = true)
  val chargeEDetails2 = ChargeEDetails(chargeAmount2, LocalDate.of(2019, 5, 1), isPaymentMandatory = false)
  val chargeCDetails = ChargeCDetails(paymentDate = QUARTER_START_DATE, amountTaxDue = chargeAmount1)
  val chargeDDetails = ChargeDDetails(QUARTER_START_DATE, Option(chargeAmount1), Option(chargeAmount2))
  val chargeGDetails = models.chargeG.ChargeDetails(qropsReferenceNumber = "123456", qropsTransferDate = QUARTER_START_DATE)
  val schemeDetails: SchemeDetails = SchemeDetails(schemeName, pstr, SchemeStatus.Open.toString, None)
  val version = "1"
  val versionInt = 1
  val version2Int = 2

  val pstrNumber = "12345678RA"
  val taxYear = new YearRange("2020")
  val taxQuarter: AFTQuarter = AFTQuarter(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 3, 31))
  val chargeAmountReported = BigDecimal(83.44)

  val sponsoringOrganisationDetails: SponsoringOrganisationDetails =
    SponsoringOrganisationDetails(name = companyName, crn = crn)
  val sponsoringIndividualDetails: MemberDetails =
    MemberDetails(firstName = "First", lastName = "Last", nino = "CS121212C")

  val sponsoringEmployerAddress: SponsoringEmployerAddress =
    SponsoringEmployerAddress(
      line1 = "line1",
      line2 = "line2",
      line3 = Some("line3"),
      line4 = Some("line4"),
      country = "GB",
      postcode = Some("ZZ1 1ZZ")
    )

  val sessionId = "1234567890"
  val lockedByName = Some(LockDetail("Name", psaId))
  val accessModeViewOnly = AccessMode.PageAccessModeViewOnly

  def sessionAccessData(version: Int = version.toInt,
                        accessMode: AccessMode = AccessMode.PageAccessModeCompile,
                        areSubmittedVersionsAvailable: Boolean = false) =
    SessionAccessData(version, accessMode, areSubmittedVersionsAvailable = areSubmittedVersionsAvailable)

  val sessionAccessDataCompile = SessionAccessData(version.toInt, AccessMode.PageAccessModeCompile, areSubmittedVersionsAvailable = false)
  val sessionAccessDataPreCompile = SessionAccessData(version.toInt, AccessMode.PageAccessModePreCompile, areSubmittedVersionsAvailable = false)

  def sessionData(
                   sessionId: String = sessionId,
                   name: Option[LockDetail] = lockedByName,
                   sessionAccessData: SessionAccessData = sessionAccessDataCompile
                 ) =
    SessionData(sessionId, lockedByName, sessionAccessData)

  def userAnswersWithSchemeName: UserAnswers =
    UserAnswers(Json.obj(
      "schemeName" -> schemeName,
      "pstr" -> pstr)
    )

  def userAnswersWithSchemeNamePstrQuarter: UserAnswers =
    UserAnswers(Json.obj(
      "schemeName" -> schemeName,
      "pstr" -> pstr,
      "quarter" -> AFTQuarter(QUARTER_START_DATE, QUARTER_END_DATE))
    )

  def uaWithPSPRAndOneSchemeAnnual: UserAnswers =
    UserAnswers(Json.obj(
      "schemeName" -> schemeName,
      "memberDetails" -> memberDetails,
      "annualAllowanceYear" -> "2020",
      "chargeDetails" -> chargeEDetails,
      "mccloudRemedy" -> Json.obj(
        "isPublicServicePensionsRemedy" -> true,
        "isChargeInAdditionReported" -> true,
        "wasAnotherPensionScheme" -> true,
        "schemes" -> Json.arr(
          Json.obj(
            "pstr" -> pstrNumber,
            "taxYearReportedAndPaidPage" -> taxYear,
            "taxQuarterReportedAndPaid" -> taxQuarter,
            "chargeAmountReported" -> chargeAmountReported
          )
        )
      )
    ))

  def uaWithPSPRAndTwoSchemesAnnual: UserAnswers =
    UserAnswers(Json.obj(
      "schemeName" -> schemeName,
      "memberDetails" -> memberDetails,
      "annualAllowanceYear" -> "2020",
      "chargeDetails" -> chargeEDetails,
      "mccloudRemedy" -> Json.obj(
        "isPublicServicePensionsRemedy" -> true,
        "isChargeInAdditionReported" -> true,
        "wasAnotherPensionScheme" -> true,
        "schemes" -> Json.arr(
          Json.obj(
            "pstr" -> "20123456RZ",
            "taxYearReportedAndPaidPage" -> taxYear,
            "taxQuarterReportedAndPaid" -> taxQuarter,
            "chargeAmountReported" -> chargeAmountReported
          ),
          Json.obj(
            "pstr" -> "20123456RQ",
            "taxYearReportedAndPaidPage" -> taxYear,
            "taxQuarterReportedAndPaid" -> taxQuarter,
            "chargeAmountReported" -> chargeAmountReported
          )
        )
      )
    ))

  def uaWithPSPRAndOneSchemeAnnualNav: UserAnswers =
    UserAnswers(Json.obj(
      "chargeEDetails" -> Json.obj(
        "members" -> Json.arr(
          Json.obj(
            "mccloudRemedy" -> Json.obj(
              "schemes" -> Json.arr(
                Json.obj(
                  "pstr" -> "20123456RZ",
                  "taxYearReportedAndPaidPage" -> taxYear,
                  "taxQuarterReportedAndPaid" -> taxQuarter,
                  "chargeAmountReported" -> chargeAmountReported,
                  "removePensionScheme" -> true
                )
              )
            ))
        ))
    ))

  def uaWithPSPRAndTwoSchemesAnnualNav: UserAnswers =
    UserAnswers(Json.obj(
      "chargeEDetails" ->Json.obj(
        "members" -> Json.arr(
          Json.obj(
            "mccloudRemedy" -> Json.obj(
              "schemes" -> Json.arr(
                Json.obj(
                  "pstr" -> "20123456RZ",
                  "taxYearReportedAndPaidPage" -> taxYear,
                  "taxQuarterReportedAndPaid" -> taxQuarter,
                  "chargeAmountReported" -> chargeAmountReported,
                  "removePensionScheme" -> true
                ),
                Json.obj(
                  "pstr" -> "20123456RQ",
                  "taxYearReportedAndPaidPage" -> taxYear,
                  "taxQuarterReportedAndPaid" -> taxQuarter,
                  "chargeAmountReported" -> chargeAmountReported,
                  "removePensionScheme" -> false
                )
              )
            ))
        ))
    ))

  def uaWithPSPRAndOneSchemeLifetimeNav: UserAnswers =
    UserAnswers(Json.obj(
      "chargeDDetails" -> Json.obj(
        "members" -> Json.arr(
          Json.obj(
            "mccloudRemedy" -> Json.obj(
              "schemes" -> Json.arr(
                Json.obj(
                  "pstr" -> "20123456RZ",
                  "taxYearReportedAndPaidPage" -> taxYear,
                  "taxQuarterReportedAndPaid" -> taxQuarter,
                  "chargeAmountReported" -> chargeAmountReported,
                  "removePensionScheme" -> true
                )
              )
            ))
        ))
    ))

  def uaWithPSPRAndTwoSchemesLifetimeNav: UserAnswers =
    UserAnswers(Json.obj(
      "chargeDDetails" -> Json.obj(
        "members" -> Json.arr(
          Json.obj(
            "mccloudRemedy" -> Json.obj(
              "schemes" -> Json.arr(
                Json.obj(
                  "pstr" -> "20123456RZ",
                  "taxYearReportedAndPaidPage" -> taxYear,
                  "taxQuarterReportedAndPaid" -> taxQuarter,
                  "chargeAmountReported" -> chargeAmountReported,
                  "removePensionScheme" -> true
                ),
                Json.obj(
                  "pstr" -> "20123456RQ",
                  "taxYearReportedAndPaidPage" -> taxYear,
                  "taxQuarterReportedAndPaid" -> taxQuarter,
                  "chargeAmountReported" -> chargeAmountReported,
                  "removePensionScheme" -> false
                )
              )
            ))
        ))
    ))

  def uaWithPSPRAndNoSchemesLifetime: UserAnswers =
    UserAnswers(Json.obj(
      "schemeName" -> schemeName,
      "memberDetails" -> memberDetails,
      "annualAllowanceYear" -> "2020",
      "chargeDetails" -> chargeDDetails,
      "mccloudRemedy" -> Json.obj(
        "isPublicServicePensionsRemedy" -> true,
        "isChargeInAdditionReported" -> true
      )
    ))

  def uaWithPSPRAndOneSchemeLifetime: UserAnswers =
    UserAnswers(Json.obj(
      "schemeName" -> schemeName,
      "memberDetails" -> memberDetails,
      "annualAllowanceYear" -> "2020",
      "chargeDetails" -> chargeDDetails,
      "mccloudRemedy" -> Json.obj(
        "isPublicServicePensionsRemedy" -> true,
        "isChargeInAdditionReported" -> true,
        "wasAnotherPensionScheme" -> true,
        "schemes" -> Json.arr(
          Json.obj(
            "pstr" -> pstrNumber,
            "taxYearReportedAndPaidPage" -> taxYear,
            "taxQuarterReportedAndPaid" -> taxQuarter,
            "chargeAmountReported" -> chargeAmountReported
          )
        )
      )
    ))

  def uaWithPSPRAndTwoSchemesLifetime: UserAnswers =
    UserAnswers(Json.obj(
      "schemeName" -> schemeName,
      "memberDetails" -> memberDetails,
      "annualAllowanceYear" -> "2020",
      "chargeDetails" -> chargeDDetails,
      "mccloudRemedy" -> Json.obj(
        "isPublicServicePensionsRemedy" -> true,
        "isChargeInAdditionReported" -> true,
        "wasAnotherPensionScheme" -> true,
        "schemes" -> Json.arr(
          Json.obj(
            "pstr" -> "20123456RZ",
            "taxYearReportedAndPaidPage" -> taxYear,
            "taxQuarterReportedAndPaid" -> taxQuarter,
            "chargeAmountReported" -> chargeAmountReported
          ),
          Json.obj(
            "pstr" -> "20123456RQ",
            "taxYearReportedAndPaidPage" -> taxYear,
            "taxQuarterReportedAndPaid" -> taxQuarter,
            "chargeAmountReported" -> chargeAmountReported
          )
        )
      )
    ))

  def userAnswersWithSchemeNameAndOrganisation: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(SponsoringOrganisationDetailsPage(0), sponsoringOrganisationDetails).toOption.get
    .set(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeOrganisation).toOption.get

  def userAnswersWithSchemeNameAndIndividual: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(SponsoringIndividualDetailsPage(0), sponsoringIndividualDetails).toOption.get
    .set(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeIndividual).toOption.get


  val chargeBDetails: ChargeBDetails = ChargeBDetails(4, chargeAmount1)
  val memberDetails: MemberDetails = MemberDetails("first", "last", "AB123456C")
  val memberDetails2: MemberDetails = MemberDetails("Joe", "Bloggs", "AB123456C")
  val memberDetails3: MemberDetails = MemberDetails("Joe", "Bliggs", "AB123457C")
  val memberDetails4: MemberDetails = MemberDetails("Joe", "Blaggs", "AB123458C")
  val memberDetails5: MemberDetails = MemberDetails("Joe", "Bleggs", "AB123458C")
  val memberDetails6: MemberDetails = MemberDetails("Joe", "Blyggs", "AB123458C")
  val memberDetails7: MemberDetails = MemberDetails("Joe", "Blyggs", "AB123458C")
  val memberGDetails: MemberDetailsG = MemberDetailsG("first", "last", LocalDate.of(2000, 4, 1), "AB123456C")
  val memberGDetails2: MemberDetailsG = MemberDetailsG("Joe", "Bloggs", LocalDate.of(2000, 4, 1), "AB123456C")

  val chargeCEmployer: UserAnswers = userAnswersWithSchemeNameAndIndividual
    .setOrException(ChargeCDetailsPage(0), chargeCDetails)
    .setOrException(TotalChargeAmountPage, chargeAmount1)

  val chargeEMember: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(MemberDetailsPage(0), memberDetails).toOption.get
    .set(ChargeDetailsPage(0), chargeEDetails).toOption.get
    .set(pages.chargeE.TotalChargeAmountPage, chargeAmount1).toOption.get

  val chargeGMember: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(pages.chargeG.MemberDetailsPage(0), memberGDetails).toOption.get
    .set(pages.chargeG.ChargeDetailsPage(0), chargeGDetails).toOption.get
    .set(pages.chargeG.ChargeAmountsPage(0), chargeAmounts).toOption.get
    .set(pages.chargeG.TotalChargeAmountPage, BigDecimal(83.44)).toOption.get

  val chargeDMember: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(ChargeDMemberDetailsPAge(0), memberDetails).toOption.get
    .set(ChargeDDetailsPage(0), chargeDDetails).toOption.get
    .set(pages.chargeD.TotalChargeAmountPage, BigDecimal(83.44)).toOption.get

  val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")

  val overview1: AFTOverview =
    AFTOverview(
      periodStartDate = LocalDate.of(2020, 4, 1),
      periodEndDate = LocalDate.of(2028, 6, 30),
      tpssReportPresent = false,
      Some(AFTOverviewVersion(
        numberOfVersions = 2,
        submittedVersionAvailable = false,
        compiledVersionAvailable = true
      )))

  val overview2: AFTOverview =
    AFTOverview(
      periodStartDate = LocalDate.of(2020, 10, 1),
      periodEndDate = LocalDate.of(2020, 12, 31),
      tpssReportPresent = false,
      Some(AFTOverviewVersion(
        numberOfVersions = 3,
        submittedVersionAvailable = false,
        compiledVersionAvailable = true
      )))

  val overview3: AFTOverview =
    AFTOverview(
      periodStartDate = LocalDate.of(2022, 1, 1),
      periodEndDate = LocalDate.of(2022, 3, 31),
      tpssReportPresent = false,
      Some(AFTOverviewVersion(
        numberOfVersions = 1,
        submittedVersionAvailable = true,
        compiledVersionAvailable = false
      )))

  val q12020: AFTQuarter = AFTQuarter(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 3, 31))
  val q22020: AFTQuarter = AFTQuarter(LocalDate.of(2020, 4, 1), LocalDate.of(2020, 6, 30))
  val q32020: AFTQuarter = AFTQuarter(LocalDate.of(2020, 7, 1), LocalDate.of(2020, 9, 30))
  val q42020: AFTQuarter = AFTQuarter(LocalDate.of(2020, 10, 1), LocalDate.of(2020, 12, 31))
  val q12021: AFTQuarter = AFTQuarter(LocalDate.of(2021, 1, 1), LocalDate.of(2021, 3, 31))

  val displayQuarterLocked: DisplayQuarter = DisplayQuarter(q32020, displayYear = false, Some(psaId), Some(LockedHint))
  val displayQuarterContinueAmend: DisplayQuarter = DisplayQuarter(q42020, displayYear = true, None, Some(InProgressHint))
  val displayQuarterViewPast: DisplayQuarter = DisplayQuarter(q22020, displayYear = false, None, Some(SubmittedHint))
  val displayQuarterStart: DisplayQuarter =
    DisplayQuarter(q12021, displayYear = false, None, None)


  val xx: Seq[DisplayQuarter] =
    Seq(
      DisplayQuarter(q12020, displayYear = false, None, None),
      DisplayQuarter(q22020, displayYear = false, None, None),
      DisplayQuarter(q32020, displayYear = false, None, None),
      DisplayQuarter(q42020, displayYear = false, None, None)
    )

  val aftOverviewQ22020: AFTOverview =
    AFTOverview(q22020.startDate, q22020.endDate,
      tpssReportPresent = false,
      Some(AFTOverviewVersion(numberOfVersions = 1, submittedVersionAvailable = true, compiledVersionAvailable = false)))
  val aftOverviewQ32020: AFTOverview =
    AFTOverview(q32020.startDate, q32020.endDate,
      tpssReportPresent = false,
      Some(AFTOverviewVersion(numberOfVersions = 1, submittedVersionAvailable = true, compiledVersionAvailable = true)))
  val aftOverviewQ42020: AFTOverview =
    AFTOverview(q42020.startDate, q42020.endDate,
      tpssReportPresent = false,
      Some(AFTOverviewVersion(numberOfVersions = 1, submittedVersionAvailable = true, compiledVersionAvailable = false)))
  val aftOverviewQ12021: AFTOverview =
    AFTOverview(q12021.startDate, q12021.endDate,
      tpssReportPresent = false,
      Some(AFTOverviewVersion(numberOfVersions = 1, submittedVersionAvailable = true, compiledVersionAvailable = false)))

  val paymentsCache: Seq[SchemeFSDetail] => PaymentsCache = schemeFSDetail => PaymentsCache(psaId, srn, schemeDetails, schemeFSDetail)
  val emptyChargesTable: Table = Table(None, Nil, firstCellIsHeader = false, Nil, Nil, Nil)

  val schemeFSResponseAftAndOTC: SchemeFS =
    SchemeFS(
      seqSchemeFSDetail = Seq(
        SchemeFSDetail(
          index = 0,
          chargeReference = "XY002610150184",
          chargeType = PSS_AFT_RETURN,
          dueDate = Some(LocalDate.parse("2020-02-15")),
          totalAmount = 12345.00,
          outstandingAmount = 56049.08,
          stoodOverAmount = 25089.08,
          amountDue = 1029.05,
          accruedInterestTotal = 23000.55,
          periodStartDate = Some(LocalDate.parse("2020-04-01")),
          periodEndDate = Some(LocalDate.parse("2020-06-30")),
          formBundleNumber = None,
          version = None,
          receiptDate = None,
          sourceChargeRefForInterest = None,
          sourceChargeInfo = None,
          documentLineItemDetails = Nil
        ),
        SchemeFSDetail(
          index = 0,
          chargeReference = "XY002610150184",
          chargeType = PSS_OTC_AFT_RETURN,
          dueDate = Some(LocalDate.parse("2020-02-15")),
          totalAmount = 56432.00,
          outstandingAmount = 56049.08,
          stoodOverAmount = 25089.08,
          amountDue = 1029.05,
          accruedInterestTotal = 24000.41,
          periodStartDate = Some(LocalDate.parse("2020-04-01")),
          periodEndDate = Some(LocalDate.parse("2020-06-30")),
          formBundleNumber = None,
          version = None,
          receiptDate = None,
          sourceChargeRefForInterest = None,
          sourceChargeInfo = None,
          documentLineItemDetails = Nil
        )
      )
    )

  val schemeFSResponseAftAndOTCWithExtraFieldValues: SchemeFS =
    SchemeFS(
      seqSchemeFSDetail = Seq(
        SchemeFSDetail(
          index = 1,
          chargeReference = "XY002610150184",
          chargeType = PSS_AFT_RETURN,
          dueDate = Some(LocalDate.parse("2020-02-15")),
          totalAmount = 12345.00,
          outstandingAmount = 56049.08,
          stoodOverAmount = 25089.08,
          amountDue = 1029.05,
          accruedInterestTotal = 23000.55,
          periodStartDate = Some(LocalDate.parse("2020-04-01")),
          periodEndDate = Some(LocalDate.parse("2020-06-30")),
          formBundleNumber = Some("12345678"),
          version = None,
          receiptDate = None,
          sourceChargeRefForInterest = None,
          sourceChargeInfo = None,
          documentLineItemDetails = Nil
        ),
        SchemeFSDetail(
          index = 2,
          chargeReference = "XY002610150185",
          chargeType = PSS_OTC_AFT_RETURN,
          dueDate = Some(LocalDate.parse("2020-02-15")),
          totalAmount = 56432.00,
          outstandingAmount = 56049.08,
          stoodOverAmount = 25089.08,
          amountDue = 129.05,
          accruedInterestTotal = 24000.41,
          periodStartDate = Some(LocalDate.parse("2020-06-01")),
          periodEndDate = Some(LocalDate.parse("2020-09-30")),
          formBundleNumber = None,
          version = None,
          receiptDate = None,
          sourceChargeRefForInterest = Some("XY002610150184"),
          sourceChargeInfo = Some(
            SchemeSourceChargeInfo(
              index = 1
            )
          ),
          documentLineItemDetails = Nil
        )
      )
    )

  val psaFsSeq: Seq[PsaFSDetail] = Seq(
    PsaFSDetail(
      index = 1,
      chargeReference = "XY002610150184",
      chargeType = OTC_6_MONTH_LPP,
      dueDate = Some(LocalDate.parse("2020-11-15")),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      accruedInterestTotal = 0.00,
      stoodOverAmount = 25089.08,
      amountDue = 100.00,
      periodStartDate = LocalDate.parse("2020-07-01"),
      periodEndDate = LocalDate.parse("2020-09-30"),
      pstr = "24000041IN",
      sourceChargeRefForInterest = None,
      documentLineItemDetails = Nil
    ),
    PsaFSDetail(
      index = 2,
      chargeReference = "XY002610150184",
      chargeType = OTC_6_MONTH_LPP,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      accruedInterestTotal = 0.00,
      stoodOverAmount = 25089.08,
      amountDue = 100.00,
      periodStartDate = LocalDate.parse("2020-10-01"),
      periodEndDate = LocalDate.parse("2020-12-31"),
      pstr = "24000041IN",
      sourceChargeRefForInterest = None,
      documentLineItemDetails = Nil
    )
  )
  val psaFs: PsaFS = PsaFS(false, psaFsSeq)
  val multiplePenalties: Seq[PsaFSDetail] = Seq(
    PsaFSDetail(
      index = 1,
      chargeReference = "XY002610150184",
      chargeType = OTC_6_MONTH_LPP,
      dueDate = Some(LocalDate.parse("2020-11-15")),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 0.00,
      amountDue = 100.00,
      periodStartDate = LocalDate.parse("2020-07-01"),
      periodEndDate = LocalDate.parse("2020-09-30"),
      pstr = "24000041IN",
      sourceChargeRefForInterest = None,
      documentLineItemDetails = Nil
    ),
    PsaFSDetail(
      index = 2,
      chargeReference = "XY002610150185",
      chargeType = CONTRACT_SETTLEMENT_INTEREST,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 0.00,
      amountDue = 100.00,
      periodStartDate = LocalDate.parse("2020-10-01"),
      periodEndDate = LocalDate.parse("2020-12-31"),
      pstr = "24000041IN",
      sourceChargeRefForInterest = None,
      documentLineItemDetails = Nil
    )
  )


}