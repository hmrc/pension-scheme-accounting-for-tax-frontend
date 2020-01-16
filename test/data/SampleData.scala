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

import models.chargeB.ChargeBDetails
import models.chargeC.{ChargeCDetails, SponsoringEmployerAddress, SponsoringIndividualDetails, SponsoringOrganisationDetails}
import models.chargeD.ChargeDDetails
import models.chargeE.ChargeEDetails
import models.chargeG.{ChargeAmounts, MemberDetails => MemberDetailsG}
import models.{MemberDetails, Quarter, SchemeDetails, UserAnswers}
import pages.chargeC.{IsSponsoringEmployerIndividualPage, SponsoringIndividualDetailsPage, SponsoringOrganisationDetailsPage}
import pages.chargeD.{ChargeDetailsPage => ChargeDDetailsPage, MemberDetailsPage => ChargeDMemberDetailsPAge}
import pages.chargeE.{ChargeDetailsPage, MemberDetailsPage}
import play.api.libs.json.Json
import play.api.mvc.Call

object SampleData {
  val userAnswersId = "id"
  val psaId = "A0000000"
  val srn = "aa"
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
  val chargeCDetails = ChargeCDetails(paymentDate = LocalDate.of(2019, 4, 3),amountTaxDue = BigDecimal(33.44))
  val chargeDDetails = ChargeDDetails(LocalDate.of(2019, 4, 3), chargeAmount1, chargeAmount2)
  val chargeGDetails = models.chargeG.ChargeDetails(qropsReferenceNumber = "Q123456", qropsTransferDate = LocalDate.of(2020, 4, 3))
  val schemeDetails: SchemeDetails = SchemeDetails(schemeName, pstr)

  val sponsoringOrganisationDetails: SponsoringOrganisationDetails =
    SponsoringOrganisationDetails(name = companyName, crn = crn)
  val sponsoringIndividualDetails: SponsoringIndividualDetails =
    SponsoringIndividualDetails(firstName = "First", lastName = "Last", nino = "CS121212C")

  val sponsoringEmployerAddress: SponsoringEmployerAddress =
    SponsoringEmployerAddress(
      line1 = "line1",
      line2 = "line2",
      line3 = Some("line3"),
      line4 = Some("line4"),
      country = "UK",
      postcode = Some("ZZ1 1ZZ")
    )

  def userAnswersWithSchemeName: UserAnswers =
    UserAnswers(Json.obj(
      "schemeName" -> schemeName,
      "pstr" -> pstr,
      "quarter" -> Quarter("2020-04-01", "2020-06-30"))
    )

  def userAnswersWithSchemeNameAndOrganisation: UserAnswers = userAnswersWithSchemeName
    .set(SponsoringOrganisationDetailsPage, sponsoringOrganisationDetails).toOption.get
    .set(IsSponsoringEmployerIndividualPage, false).toOption.get

  def userAnswersWithSchemeNameAndIndividual: UserAnswers = userAnswersWithSchemeName
    .set(SponsoringIndividualDetailsPage, sponsoringIndividualDetails).toOption.get
    .set(IsSponsoringEmployerIndividualPage, true).toOption.get


  val chargeBDetails: ChargeBDetails = ChargeBDetails(4, chargeAmount1)
  val memberDetails: MemberDetails = MemberDetails("first", "last", "AB123456C")
  val memberDetails2: MemberDetails = MemberDetails("Joe", "Bloggs", "AB123456C")
  val memberGDetails: MemberDetailsG = MemberDetailsG("first", "last", LocalDate.now(), "AB123456C")
  val memberGDetails2: MemberDetailsG = MemberDetailsG("Joe", "Bloggs", LocalDate.now(), "AB123456C")
  val memberDetailsDeleted: MemberDetails = MemberDetails("Jill", "Bloggs", "AB123456C", isDeleted = true)
  val memberGDetailsDeleted: MemberDetailsG = MemberDetailsG("Jill", "Bloggs", LocalDate.now(), "AB123456C", isDeleted = true)

  val chargeEMember: UserAnswers = userAnswersWithSchemeName
    .set(MemberDetailsPage(0), memberDetails).toOption.get
    .set(ChargeDetailsPage(0), chargeEDetails).toOption.get

  val chargeGMember: UserAnswers = userAnswersWithSchemeName
    .set(pages.chargeG.MemberDetailsPage(0), memberGDetails).toOption.get
    .set(pages.chargeG.ChargeDetailsPage(0), chargeGDetails).toOption.get
    .set(pages.chargeG.ChargeAmountsPage(0), chargeAmounts).toOption.get

  val chargeDMember: UserAnswers = userAnswersWithSchemeName
    .set(ChargeDMemberDetailsPAge(0), memberDetails).toOption.get
    .set(ChargeDDetailsPage(0), chargeDDetails).toOption.get
}
