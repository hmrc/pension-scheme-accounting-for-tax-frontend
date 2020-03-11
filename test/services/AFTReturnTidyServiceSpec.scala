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

import base.SpecBase
import data.SampleData
import data.SampleData._
import models.UserAnswers
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeC.{ChargeCDetailsPage, WhichTypeOfSponsoringEmployerPage, SponsoringIndividualDetailsPage, SponsoringOrganisationDetailsPage}
import play.api.mvc.Results

class AFTReturnTidyServiceSpec extends SpecBase with ScalaFutures with BeforeAndAfterEach with MockitoSugar with Results {
  private val zeroCurrencyValue = BigDecimal(0.00)
  val aftReturnTidyService = new AFTReturnTidyService()

  val uaWithAllMemberBasedCharges: UserAnswers = userAnswersWithSchemeName
    .setOrException(pages.chargeE.ChargeDetailsPage(0), chargeEDetails)
    .setOrException(pages.chargeE.MemberDetailsPage(0), memberDetails)
    .setOrException(pages.chargeD.ChargeDetailsPage(0), chargeDDetails)
    .setOrException(pages.chargeD.MemberDetailsPage(0), memberDetails)
    .setOrException(pages.chargeG.ChargeDetailsPage(0), chargeGDetails)
    .setOrException(pages.chargeG.MemberDetailsPage(0), memberGDetails)
    .setOrException(pages.chargeC.ChargeCDetailsPage(0), chargeCDetails)
    .setOrException(WhichTypeOfSponsoringEmployerPage(0), true)
    .setOrException(SponsoringIndividualDetailsPage(0), sponsoringIndividualDetails)

  "isAtLeastOneValidCharge" must {
    "return true where there is only a charge type A present" in {
      val ua = SampleData.userAnswersWithSchemeName
        .setOrException(pages.chargeA.ChargeDetailsPage, chargeAChargeDetails)
      aftReturnTidyService.isAtLeastOneValidCharge(ua) mustBe true
    }

    "return true where there is only a charge type B present" in {
      val ua = SampleData.userAnswersWithSchemeName
        .setOrException(pages.chargeB.ChargeBDetailsPage, SampleData.chargeBDetails)
      aftReturnTidyService.isAtLeastOneValidCharge(ua) mustBe true
    }

    "return true where there is only a charge type C present" in {
      val ua = SampleData.userAnswersWithSchemeName
        .setOrException(pages.chargeC.ChargeCDetailsPage(0), chargeCDetails)
        .setOrException(WhichTypeOfSponsoringEmployerPage(0), true)
        .setOrException(SponsoringIndividualDetailsPage(0), sponsoringIndividualDetails)
      aftReturnTidyService.isAtLeastOneValidCharge(ua) mustBe true
    }

    "return true where there is only a charge type D present" in {
      val ua = SampleData.userAnswersWithSchemeName
        .setOrException(pages.chargeD.MemberDetailsPage(0), memberDetails)
      aftReturnTidyService.isAtLeastOneValidCharge(ua) mustBe true
    }

    "return true where there is only a charge type E present" in {
      val ua = SampleData.userAnswersWithSchemeName
        .setOrException(pages.chargeE.MemberDetailsPage(0), memberDetails)
      aftReturnTidyService.isAtLeastOneValidCharge(ua) mustBe true
    }

    "return true where there is only a charge type F present" in {
      val ua = SampleData.userAnswersWithSchemeName
        .setOrException(pages.chargeF.ChargeDetailsPage, chargeFChargeDetails)
      aftReturnTidyService.isAtLeastOneValidCharge(ua) mustBe true
    }

    "return true where there is only a charge type G present" in {
      val ua = SampleData.userAnswersWithSchemeName
        .setOrException(pages.chargeG.MemberDetailsPage(0), memberGDetails)
      aftReturnTidyService.isAtLeastOneValidCharge(ua) mustBe true
    }

    "return false where there are no charges present" in {
      val ua = SampleData.userAnswersWithSchemeName
      aftReturnTidyService.isAtLeastOneValidCharge(ua) mustBe false
    }

    "return false where there is only a charge type C present with one employer which is deleted" in {
      val ua = SampleData.userAnswersWithSchemeName
        .setOrException(pages.chargeC.ChargeCDetailsPage(0), chargeCDetails)
        .setOrException(WhichTypeOfSponsoringEmployerPage(0), true)
        .setOrException(SponsoringIndividualDetailsPage(0), sponsoringIndividualDetailsDeleted)
      aftReturnTidyService.isAtLeastOneValidCharge(ua) mustBe false
    }

    "return false where there is only a charge type D present with one member which is deleted" in {
      val ua = SampleData.userAnswersWithSchemeName
        .setOrException(pages.chargeD.MemberDetailsPage(0), memberDetailsDeleted)
      aftReturnTidyService.isAtLeastOneValidCharge(ua) mustBe false
    }

    "return false where there is only a charge type E present with one member which is deleted" in {
      val ua = SampleData.userAnswersWithSchemeName
        .setOrException(pages.chargeE.MemberDetailsPage(0), memberDetailsDeleted)
      aftReturnTidyService.isAtLeastOneValidCharge(ua) mustBe false
    }

    "return false where there is only a charge type G present with one member which is deleted" in {
      val ua = SampleData.userAnswersWithSchemeName
        .setOrException(pages.chargeG.MemberDetailsPage(0), memberGDetailsDeleted)
      aftReturnTidyService.isAtLeastOneValidCharge(ua) mustBe false
    }

  }

  "removeChargesHavingNoMembersOrEmployers" must {
    "NOT remove member-based charges which are not deleted" in {
      val result = aftReturnTidyService.removeChargesHavingNoMembersOrEmployers(uaWithAllMemberBasedCharges)
      (result.data \ "chargeEDetails").toOption.isDefined mustBe true
      (result.data \ "chargeDDetails").toOption.isDefined mustBe true
      (result.data \ "chargeGDetails").toOption.isDefined mustBe true
      (result.data \ "chargeCDetails").toOption.isDefined mustBe true
    }

    "remove charge E where it has no non-deleted members and another valid charge is present" in {
      val ua: UserAnswers = userAnswersWithSchemeName
        .setOrException(pages.chargeE.ChargeDetailsPage(0), chargeEDetails)
        .setOrException(pages.chargeE.MemberDetailsPage(0), memberDetailsDeleted)
        .setOrException(pages.chargeF.ChargeDetailsPage, chargeFChargeDetails)

      val result = aftReturnTidyService.removeChargesHavingNoMembersOrEmployers(ua)

      (result.data \ "chargeEDetails").toOption mustBe None
    }


    "remove charge D where it has no non-deleted members and another valid charge is present" in {
      val ua: UserAnswers = userAnswersWithSchemeName
        .setOrException(pages.chargeD.ChargeDetailsPage(0), chargeDDetails)
        .setOrException(pages.chargeD.MemberDetailsPage(0), memberDetailsDeleted)
        .setOrException(pages.chargeF.ChargeDetailsPage, chargeFChargeDetails)

      val result = aftReturnTidyService.removeChargesHavingNoMembersOrEmployers(ua)
      (result.data \ "chargeDDetails").toOption mustBe None
    }


    "remove charge G where it has no non-deleted members and another valid charge is present" in {
      val ua: UserAnswers = userAnswersWithSchemeName
        .setOrException(pages.chargeG.ChargeDetailsPage(0), chargeGDetails)
        .setOrException(pages.chargeG.MemberDetailsPage(0), memberGDetailsDeleted)
        .setOrException(pages.chargeF.ChargeDetailsPage, chargeFChargeDetails)
      val result = aftReturnTidyService.removeChargesHavingNoMembersOrEmployers(ua)
      (result.data \ "chargeGDetails").toOption mustBe None
    }


    "remove charge C where it has no non-deleted members and another valid charge is present for individual" in {
      val ua: UserAnswers = userAnswersWithSchemeName
        .setOrException(pages.chargeC.ChargeCDetailsPage(0), chargeCDetails)
        .setOrException(WhichTypeOfSponsoringEmployerPage(0), true)
        .setOrException(SponsoringIndividualDetailsPage(0), sponsoringIndividualDetailsDeleted)
        .setOrException(pages.chargeF.ChargeDetailsPage, chargeFChargeDetails)
      val result = aftReturnTidyService.removeChargesHavingNoMembersOrEmployers(ua)
      (result.data \ "chargeCDetails").toOption mustBe None
    }


    "remove charge C where it has no non-deleted members and another valid charge is present for organisation" in {
      val ua: UserAnswers = userAnswersWithSchemeName
        .setOrException(pages.chargeC.ChargeCDetailsPage(0), chargeCDetails)
        .setOrException(WhichTypeOfSponsoringEmployerPage(0), false)
        .setOrException(SponsoringOrganisationDetailsPage(0), sponsoringOrganisationDetailsDeleted)
        .setOrException(pages.chargeF.ChargeDetailsPage, chargeFChargeDetails)
      val result = aftReturnTidyService.removeChargesHavingNoMembersOrEmployers(ua)
      (result.data \ "chargeCDetails").toOption mustBe None
    }
  }

  "reinstateDeletedMemberOrEmployer" must {
    "reinstate only the last deleted member from charge C and zero currency value" in {
      val ua: UserAnswers = userAnswersWithSchemeName
        .setOrException(pages.chargeC.ChargeCDetailsPage(0), chargeCDetails)
        .setOrException(pages.chargeC.ChargeCDetailsPage(1), chargeCDetails)
        .setOrException(WhichTypeOfSponsoringEmployerPage(0), true)
        .setOrException(SponsoringIndividualDetailsPage(0), sponsoringIndividualDetailsDeleted)
        .setOrException(WhichTypeOfSponsoringEmployerPage(1), true)
        .setOrException(SponsoringIndividualDetailsPage(1), sponsoringIndividualDetailsDeleted)

      val result = aftReturnTidyService.reinstateDeletedMemberOrEmployer(ua)
      result.get(SponsoringIndividualDetailsPage(0)).map(_.isDeleted) mustBe Some(true)
      result.get(SponsoringIndividualDetailsPage(1)).map(_.isDeleted) mustBe Some(false)
      result.get(ChargeCDetailsPage(0)).map(_.amountTaxDue) mustBe Some(chargeCDetails.amountTaxDue)
      result.get(ChargeCDetailsPage(1)).map(_.amountTaxDue) mustBe Some(zeroCurrencyValue)
    }

    "reinstate only the last deleted member from charge D and zero currency values" in {
      val ua: UserAnswers = userAnswersWithSchemeName
        .setOrException(pages.chargeD.ChargeDetailsPage(0), chargeDDetails)
        .setOrException(pages.chargeD.ChargeDetailsPage(1), chargeDDetails)
        .setOrException(pages.chargeD.MemberDetailsPage(0), memberDetailsDeleted)
        .setOrException(pages.chargeD.MemberDetailsPage(1), memberDetailsDeleted)


      val result = aftReturnTidyService.reinstateDeletedMemberOrEmployer(ua)
      result.get(pages.chargeD.MemberDetailsPage(0)).map(_.isDeleted) mustBe Some(true)
      result.get(pages.chargeD.MemberDetailsPage(1)).map(_.isDeleted) mustBe Some(false)
      result.get(pages.chargeD.ChargeDetailsPage(0)).flatMap(_.taxAt25Percent) mustBe chargeDDetails.taxAt25Percent
      result.get(pages.chargeD.ChargeDetailsPage(0)).flatMap(_.taxAt55Percent) mustBe chargeDDetails.taxAt55Percent
      result.get(pages.chargeD.ChargeDetailsPage(1)).flatMap(_.taxAt25Percent) mustBe Some(zeroCurrencyValue)
      result.get(pages.chargeD.ChargeDetailsPage(1)).flatMap(_.taxAt55Percent) mustBe Some(zeroCurrencyValue)
    }

    "reinstate only the last deleted member from charge E and zero currency values" in {
      val ua: UserAnswers = userAnswersWithSchemeName
        .setOrException(pages.chargeE.ChargeDetailsPage(0), chargeEDetails)
        .setOrException(pages.chargeE.ChargeDetailsPage(1), chargeEDetails)
        .setOrException(pages.chargeE.MemberDetailsPage(0), memberDetailsDeleted)
        .setOrException(pages.chargeE.MemberDetailsPage(1), memberDetailsDeleted)


      val result = aftReturnTidyService.reinstateDeletedMemberOrEmployer(ua)
      result.get(pages.chargeE.MemberDetailsPage(0)).map(_.isDeleted) mustBe Some(true)
      result.get(pages.chargeE.MemberDetailsPage(1)).map(_.isDeleted) mustBe Some(false)
      result.get(pages.chargeE.ChargeDetailsPage(0)).map(_.chargeAmount) mustBe Some(chargeEDetails.chargeAmount)
      result.get(pages.chargeE.ChargeDetailsPage(1)).map(_.chargeAmount) mustBe Some(zeroCurrencyValue)
    }

    "reinstate only the last deleted member from charge G and zero currency values" in {
      val ua: UserAnswers = userAnswersWithSchemeName
        .setOrException(pages.chargeG.ChargeAmountsPage(0), chargeAmounts)
        .setOrException(pages.chargeG.ChargeAmountsPage(1), chargeAmounts)
        .setOrException(pages.chargeG.MemberDetailsPage(0), memberGDetailsDeleted)
        .setOrException(pages.chargeG.MemberDetailsPage(1), memberGDetailsDeleted)


      val result = aftReturnTidyService.reinstateDeletedMemberOrEmployer(ua)
      result.get(pages.chargeG.MemberDetailsPage(0)).map(_.isDeleted) mustBe Some(true)
      result.get(pages.chargeG.MemberDetailsPage(1)).map(_.isDeleted) mustBe Some(false)
      result.get(pages.chargeG.ChargeAmountsPage(0)).map(_.amountTaxDue) mustBe Some(chargeAmounts.amountTaxDue)
      result.get(pages.chargeG.ChargeAmountsPage(0)).map(_.amountTransferred) mustBe Some(chargeAmounts.amountTransferred)
      result.get(pages.chargeG.ChargeAmountsPage(1)).map(_.amountTaxDue) mustBe Some(zeroCurrencyValue)
      result.get(pages.chargeG.ChargeAmountsPage(1)).map(_.amountTransferred) mustBe Some(zeroCurrencyValue)
    }

    "do nothing if there is no member-based charge to reinstate" in {
      val result = aftReturnTidyService.reinstateDeletedMemberOrEmployer(uaWithAllMemberBasedCharges)
      result.data mustBe uaWithAllMemberBasedCharges.data
    }
  }
}
