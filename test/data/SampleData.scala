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

package data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import models.SponsoringEmployerType.{SponsoringEmployerTypeIndividual, SponsoringEmployerTypeOrganisation}
import models.chargeB.ChargeBDetails
import models.chargeC.{ChargeCDetails, SponsoringEmployerAddress, SponsoringOrganisationDetails}
import models.chargeD.ChargeDDetails
import models.chargeE.ChargeEDetails
import models.chargeG.{ChargeAmounts, MemberDetails => MemberDetailsG}
import models.{AFTOverview, MemberDetails, Quarter, SchemeDetails, SchemeStatus, UserAnswers}
import pages.chargeC.{ChargeCDetailsPage, SponsoringIndividualDetailsPage, SponsoringOrganisationDetailsPage, WhichTypeOfSponsoringEmployerPage}
import pages.chargeD.{ChargeDetailsPage => ChargeDDetailsPage, MemberDetailsPage => ChargeDMemberDetailsPAge}
import pages.chargeE.{ChargeDetailsPage, MemberDetailsPage}
import play.api.libs.json.Json
import play.api.mvc.Call
import utils.AFTConstants._

object SampleData {
  val userAnswersId = "id"
  val psaId = "A0000000"
  val srn = "aa"
  val startDate = QUARTER_START_DATE
  val pstr = "pstr"
  val schemeName = "Big Scheme"
  val companyName = "Big Company"
  val crn = "AB121212"
  val dummyCall: Call = Call("GET", "/foo")
  val chargeAmount1 = BigDecimal(33.44)
  val chargeAmount2 = BigDecimal(50.00)
  val chargeAmounts = ChargeAmounts(chargeAmount1, chargeAmount2)
  val chargeAmounts2 = ChargeAmounts(chargeAmount1, chargeAmount2)
  val chargeFChargeDetails = models.chargeF.ChargeDetails(LocalDate.of(2020, 4, 3), BigDecimal(33.44))
  val chargeAChargeDetails = models.chargeA.ChargeDetails(44, Some(BigDecimal(33.44)), Some(BigDecimal(34.34)), BigDecimal(67.78))
  val chargeEDetails = ChargeEDetails(chargeAmount1, LocalDate.of(2019, 4, 3), isPaymentMandatory = true)
  val chargeCDetails = ChargeCDetails(paymentDate = QUARTER_START_DATE,amountTaxDue = BigDecimal(33.44))
  val chargeDDetails = ChargeDDetails(QUARTER_START_DATE, Option(chargeAmount1), Option(chargeAmount2))
  val chargeGDetails = models.chargeG.ChargeDetails(qropsReferenceNumber = "123456", qropsTransferDate = QUARTER_START_DATE)
  val schemeDetails: SchemeDetails = SchemeDetails(schemeName, pstr, SchemeStatus.Open.toString)
  val version = "1"

  val sponsoringOrganisationDetails: SponsoringOrganisationDetails =
    SponsoringOrganisationDetails(name = companyName, crn = crn)
  val sponsoringIndividualDetails: MemberDetails =
    MemberDetails(firstName = "First", lastName = "Last", nino = "CS121212C")

  val sponsoringIndividualDetailsDeleted: MemberDetails =
    MemberDetails(firstName = "First", lastName = "Last", nino = "CS121212C", isDeleted = true)
  val sponsoringOrganisationDetailsDeleted: SponsoringOrganisationDetails =
    SponsoringOrganisationDetails(name = companyName, crn = crn, isDeleted = true)

  val sponsoringEmployerAddress: SponsoringEmployerAddress =
    SponsoringEmployerAddress(
      line1 = "line1",
      line2 = "line2",
      line3 = Some("line3"),
      line4 = Some("line4"),
      country = "GB",
      postcode = Some("ZZ1 1ZZ")
    )

  def userAnswersWithSchemeName: UserAnswers =
    UserAnswers(Json.obj(
      "schemeName" -> schemeName,
      "pstr" -> pstr)
    )

  def userAnswersWithSchemeNamePSTRAndVersion: UserAnswers =
    UserAnswers(Json.obj(
      "schemeName" -> schemeName,
      "pstr" -> pstr,
    "versionNumber" -> 3)
    )

  def userAnswersWithSchemeNamePstrQuarter: UserAnswers =
    UserAnswers(Json.obj(
      "schemeName" -> schemeName,
      "pstr" -> pstr,
      "quarter" -> Quarter(QUARTER_START_DATE, QUARTER_END_DATE))
    )

  def userAnswersWithSchemeNameAndOrganisation: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(SponsoringOrganisationDetailsPage(0), sponsoringOrganisationDetails).toOption.get
    .set(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeOrganisation).toOption.get

  def userAnswersWithSchemeNameAndIndividual: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(SponsoringIndividualDetailsPage(0), sponsoringIndividualDetails).toOption.get
    .set(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeIndividual).toOption.get


  val chargeBDetails: ChargeBDetails = ChargeBDetails(4, chargeAmount1)
  val memberDetails: MemberDetails = MemberDetails("first", "last", "AB123456C")
  val memberDetails2: MemberDetails = MemberDetails("Joe", "Bloggs", "AB123456C")
  val memberGDetails: MemberDetailsG = MemberDetailsG("first", "last", LocalDate.now(), "AB123456C")
  val memberGDetails2: MemberDetailsG = MemberDetailsG("Joe", "Bloggs", LocalDate.now(), "AB123456C")
  val memberDetailsDeleted: MemberDetails = MemberDetails("Jill", "Bloggs", "AB123456C", isDeleted = true)
  val memberGDetailsDeleted: MemberDetailsG = MemberDetailsG("Jill", "Bloggs", LocalDate.now(), "AB123456C", isDeleted = true)

  val chargeCEmployer: UserAnswers = userAnswersWithSchemeNameAndIndividual
    .set(ChargeCDetailsPage(0), chargeCDetails).toOption.get

  val chargeEMember: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(MemberDetailsPage(0), memberDetails).toOption.get
    .set(ChargeDetailsPage(0), chargeEDetails).toOption.get

  val chargeGMember: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(pages.chargeG.MemberDetailsPage(0), memberGDetails).toOption.get
    .set(pages.chargeG.ChargeDetailsPage(0), chargeGDetails).toOption.get
    .set(pages.chargeG.ChargeAmountsPage(0), chargeAmounts).toOption.get

  val chargeDMember: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(ChargeDMemberDetailsPAge(0), memberDetails).toOption.get
    .set(ChargeDDetailsPage(0), chargeDDetails).toOption.get

  val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")

  val overview1: AFTOverview =
    AFTOverview(
      periodStartDate = LocalDate.of(2020,4,1),
      periodEndDate = LocalDate.of(2028,6,30),
      numberOfVersions = 2,
      submittedVersionAvailable = false,
      compiledVersionAvailable = true
    )

  val overview2: AFTOverview =
    AFTOverview(
      periodStartDate = LocalDate.of(2020,10,1),
      periodEndDate = LocalDate.of(2020,12,31),
      numberOfVersions = 3,
      submittedVersionAvailable = false,
      compiledVersionAvailable = true
    )

  val overview3: AFTOverview =
    AFTOverview(
      periodStartDate = LocalDate.of(2022,1,1),
      periodEndDate = LocalDate.of(2022,3,31),
      numberOfVersions = 1,
      submittedVersionAvailable = true,
      compiledVersionAvailable = false
    )
}
