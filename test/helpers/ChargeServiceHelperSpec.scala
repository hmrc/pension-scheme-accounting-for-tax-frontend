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

package helpers

import base.SpecBase
import data.SampleData
import models.{AccessMode, AmendedChargeStatus, UserAnswers}
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import pages.chargeC._
import pages.{chargeD, chargeE, chargeG}

class ChargeServiceHelperSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach {

  import ChargeServiceHelperSpec._

  private val chargeServiceHelper = new ChargeServiceHelper

  "totalAmount in charge C" must {
    "return total amount in charge C when it is not the last charge" in {
        chargeServiceHelper.totalAmount(allEmployersChargeC, "chargeCDetails") mustBe 66.88
    }

    "return total amount in charge C when it has deleted charge" in {
      chargeServiceHelper.totalAmount(allEmployersChargeCIncludingDeleted, "chargeCDetails") mustBe 66.88
    }

    "return total amount as zero in Charge C when it has only deleted charge" in {
      chargeServiceHelper.totalAmount(deletedEmployersChargeC, "chargeCDetails") mustBe 0
    }
  }

  "isEmployerPresent in charge C" must {
    "return true when any non-deleted employee present in charge C" in {
      chargeServiceHelper.isEmployerOrMemberPresent(allEmployersChargeCIncludingDeleted, "chargeCDetails") mustBe true
    }

    "return false when only deleted employee present in charge C" in {
      chargeServiceHelper.isEmployerOrMemberPresent(deletedEmployersChargeC, "chargeCDetails") mustBe false
    }
  }

  "totalAmount in charge D" must {
    "return total amount in charge D when it is not the last charge" in {
      chargeServiceHelper.totalAmount(allMembersChargeD, "chargeDDetails") mustBe 166.88
    }

    "return total amount in charge D when it has deleted charge" in {
      chargeServiceHelper.totalAmount(allMembersChargeDIncludingDeleted, "chargeDDetails") mustBe 166.88
    }

    "return total amount as zero in Charge D when it has only deleted charge" in {
      chargeServiceHelper.totalAmount(deletedEmployersChargeD, "chargeDDetails") mustBe 0
    }
  }

  "isEmployerPresent in charge D" must {
    "return true when any non-deleted employee present in charge D" in {
      chargeServiceHelper.isEmployerOrMemberPresent(allMembersChargeDIncludingDeleted, "chargeDDetails") mustBe true
    }

    "return false when only deleted employee present in charge D" in {
      chargeServiceHelper.isEmployerOrMemberPresent(deletedEmployersChargeD, "chargeDDetails") mustBe false
    }
  }

  "totalAmount in charge E" must {
    "return total amount in charge E when it is not the last charge" in {
      chargeServiceHelper.totalAmount(allEmployersChargeE, "chargeEDetails") mustBe 66.88
    }

    "return total amount in charge E when it has deleted charge" in {
      chargeServiceHelper.totalAmount(allEmployersChargeEIncludingDeleted, "chargeEDetails") mustBe 66.88
    }

    "return total amount as zero in Charge E when it has only deleted charge" in {
      chargeServiceHelper.totalAmount(deletedEmployersChargeE, "chargeEDetails") mustBe 0
    }
  }

  "isEmployerPresent in charge E" must {
    "return true when any non-deleted employee present in charge E" in {
      chargeServiceHelper.isEmployerOrMemberPresent(allEmployersChargeEIncludingDeleted, "chargeEDetails") mustBe true
    }

    "return false when only deleted employee present in charge E" in {
      chargeServiceHelper.isEmployerOrMemberPresent(deletedEmployersChargeE, "chargeEDetails") mustBe false
    }
  }

  "totalAmount in charge G" must {
    "return total amount in charge G when it is not the last charge" in {
      chargeServiceHelper.totalAmount(allEmployersChargeG, "chargeGDetails") mustBe 100.0
    }

    "return total amount in charge G when it has deleted charge" in {
      chargeServiceHelper.totalAmount(allEmployersChargeGIncludingDeleted, "chargeGDetails") mustBe 100.0
    }

    "return total amount as zero in Charge G when it has only deleted charge" in {
      chargeServiceHelper.totalAmount(deletedEmployersChargeG, "chargeGDetails") mustBe 0
    }
  }

  "isEmployerPresent in charge G" must {
    "return true when any non-deleted employee present in charge G" in {
      chargeServiceHelper.isEmployerOrMemberPresent(allEmployersChargeGIncludingDeleted, "chargeGDetails") mustBe true
    }

    "return false when only deleted employee present in charge G" in {
      chargeServiceHelper.isEmployerOrMemberPresent(deletedEmployersChargeG, "chargeGDetails") mustBe false
    }
  }

  "isShowFileUploadOption" must {
    "return false when charge not exists" in {
      chargeServiceHelper.isShowFileUploadOption(UserAnswers(), "chargeADetails",1,AccessMode.PageAccessModePreCompile) mustBe false
    }

    "return false when charge member exists with Access mode PreCompile and version more than 1" in {
      chargeServiceHelper.isShowFileUploadOption(allEmployersChargeE, "chargeEDetails",2,AccessMode.PageAccessModePreCompile) mustBe false
    }
    "return true when charge member exists with Access mode PreCompile and version 1" in {
      chargeServiceHelper.isShowFileUploadOption(allEmployersChargeE, "chargeEDetails",1,AccessMode.PageAccessModePreCompile) mustBe true
    }

    "return false when Access mode Compile with memberAftVersion less than aft version" in {
      chargeServiceHelper.isShowFileUploadOption(allEmployersChargeEWithVersionAndStatus(1,"Deleted"),
        "chargeEDetails",2,AccessMode.PageAccessModeCompile) mustBe false
    }
    "return true when  Access mode Compile with member added in same version " in {
      chargeServiceHelper.isShowFileUploadOption(allEmployersChargeEWithVersionAndStatus(2,"New"),
        "chargeEDetails",2,AccessMode.PageAccessModeCompile) mustBe true
    }
    "return true when charge member exists with Access mode Compile and member status New" in {
      chargeServiceHelper.isShowFileUploadOption(allEmployersChargeEWithVersionAndStatus(3,"New"), "chargeEDetails",2,AccessMode.PageAccessModeCompile) mustBe true
    }
  }

}

object ChargeServiceHelperSpec {

  val allEmployersChargeC: UserAnswers = UserAnswers()
    .set(MemberStatusPage(0), AmendedChargeStatus.Added.toString).toOption.get
    .set(SponsoringIndividualDetailsPage(0), SampleData.sponsoringIndividualDetails).toOption.get
    .set(ChargeCDetailsPage(0), SampleData.chargeCDetails).toOption.get
    .set(MemberStatusPage(1), AmendedChargeStatus.Updated.toString).toOption.get
    .set(SponsoringOrganisationDetailsPage(1), SampleData.sponsoringOrganisationDetails).toOption.get
    .set(ChargeCDetailsPage(1), SampleData.chargeCDetails).toOption.get

  val allEmployersChargeCIncludingDeleted: UserAnswers = allEmployersChargeC
    .set(MemberStatusPage(2), AmendedChargeStatus.Deleted.toString).toOption.get
    .set(SponsoringIndividualDetailsPage(2), SampleData.memberDetails).toOption.get
    .set(ChargeCDetailsPage(2), SampleData.chargeCDetails).toOption.get

  val deletedEmployersChargeC: UserAnswers = UserAnswers()
    .set(MemberStatusPage(0), AmendedChargeStatus.Deleted.toString).toOption.get
    .set(SponsoringIndividualDetailsPage(0), SampleData.memberDetails).toOption.get
    .set(ChargeCDetailsPage(0), SampleData.chargeCDetails).toOption.get
    .set(MemberStatusPage(1), AmendedChargeStatus.Deleted.toString).toOption.get
    .set(SponsoringIndividualDetailsPage(1), SampleData.memberDetails).toOption.get
    .set(ChargeCDetailsPage(1), SampleData.chargeCDetails).toOption.get

  val allMembersChargeD: UserAnswers = UserAnswers()
    .set(chargeD.MemberStatusPage(0), AmendedChargeStatus.Added.toString).toOption.get
    .set(chargeD.MemberDetailsPage(0), SampleData.memberDetails).toOption.get
    .set(chargeD.ChargeDetailsPage(0), SampleData.chargeDDetails).toOption.get
    .set(chargeD.MemberStatusPage(1), AmendedChargeStatus.Updated.toString).toOption.get
    .set(chargeD.MemberDetailsPage(1), SampleData.memberDetails2).toOption.get
    .set(chargeD.ChargeDetailsPage(1), SampleData.chargeDDetails).toOption.get

  val allMembersChargeDIncludingDeleted: UserAnswers = allMembersChargeD
    .set(chargeD.MemberStatusPage(2), AmendedChargeStatus.Deleted.toString).toOption.get
    .set(chargeD.MemberDetailsPage(2), SampleData.memberDetails).toOption.get
    .set(chargeD.ChargeDetailsPage(2), SampleData.chargeDDetails).toOption.get

  val deletedEmployersChargeD: UserAnswers = UserAnswers()
  .set(chargeD.MemberStatusPage(0), AmendedChargeStatus.Deleted.toString).toOption.get
  .set(chargeD.MemberDetailsPage(0), SampleData.memberDetails).toOption.get
  .set(chargeD.ChargeDetailsPage(0), SampleData.chargeDDetails).toOption.get
  .set(chargeD.MemberStatusPage(1), AmendedChargeStatus.Deleted.toString).toOption.get
  .set(chargeD.MemberDetailsPage(1), SampleData.memberDetails2).toOption.get
  .set(chargeD.ChargeDetailsPage(1), SampleData.chargeDDetails).toOption.get

  val allEmployersChargeE: UserAnswers = UserAnswers()
    .set(chargeE.MemberStatusPage(0), AmendedChargeStatus.Added.toString).toOption.get
    .set(chargeE.MemberDetailsPage(0), SampleData.memberDetails).toOption.get
    .set(chargeE.ChargeDetailsPage(0), SampleData.chargeEDetails).toOption.get
    .set(chargeE.MemberStatusPage(1), AmendedChargeStatus.Updated.toString).toOption.get
    .set(chargeE.MemberDetailsPage(1), SampleData.memberDetails2).toOption.get
    .set(chargeE.ChargeDetailsPage(1), SampleData.chargeEDetails).toOption.get

  val allEmployersChargeEIncludingDeleted: UserAnswers = allEmployersChargeE
    .set(chargeE.MemberStatusPage(2), AmendedChargeStatus.Deleted.toString).toOption.get
    .set(chargeE.MemberDetailsPage(2), SampleData.memberDetails3).toOption.get
    .set(chargeE.ChargeDetailsPage(2), SampleData.chargeEDetails).toOption.get

  val deletedEmployersChargeE: UserAnswers = UserAnswers()
    .set(chargeE.MemberStatusPage(0), AmendedChargeStatus.Deleted.toString).toOption.get
    .set(chargeE.MemberDetailsPage(0), SampleData.memberDetails).toOption.get
    .set(chargeE.ChargeDetailsPage(0), SampleData.chargeEDetails).toOption.get
    .set(chargeE.MemberStatusPage(1), AmendedChargeStatus.Deleted.toString).toOption.get
    .set(chargeE.MemberDetailsPage(1), SampleData.memberDetails2).toOption.get
    .set(chargeE.ChargeDetailsPage(1), SampleData.chargeEDetails).toOption.get

  val allEmployersChargeG: UserAnswers = UserAnswers()
    .set(chargeG.MemberStatusPage(0), AmendedChargeStatus.Added.toString).toOption.get
    .set(chargeG.MemberDetailsPage(0), SampleData.memberGDetails).toOption.get
    .set(chargeG.ChargeAmountsPage(0), SampleData.chargeAmounts).toOption.get
    .set(chargeG.MemberStatusPage(1), AmendedChargeStatus.Updated.toString).toOption.get
    .set(chargeG.MemberDetailsPage(1), SampleData.memberGDetails2).toOption.get
    .set(chargeG.ChargeAmountsPage(1), SampleData.chargeAmounts2).toOption.get

  val allEmployersChargeGIncludingDeleted: UserAnswers = allEmployersChargeG
    .set(chargeG.MemberStatusPage(2), AmendedChargeStatus.Deleted.toString).toOption.get
    .set(chargeG.MemberDetailsPage(2), SampleData.memberGDetails).toOption.get
    .set(chargeG.ChargeAmountsPage(2), SampleData.chargeAmounts).toOption.get

  val deletedEmployersChargeG: UserAnswers = UserAnswers()
    .set(chargeG.MemberStatusPage(0), AmendedChargeStatus.Deleted.toString).toOption.get
    .set(chargeG.MemberDetailsPage(0), SampleData.memberGDetails).toOption.get
    .set(chargeG.ChargeAmountsPage(0), SampleData.chargeAmounts).toOption.get
    .set(chargeG.MemberStatusPage(1), AmendedChargeStatus.Deleted.toString).toOption.get
    .set(chargeG.MemberDetailsPage(1), SampleData.memberGDetails2).toOption.get
    .set(chargeG.ChargeAmountsPage(1), SampleData.chargeAmounts2).toOption.get

  val chargeWithoutMember: UserAnswers = UserAnswers()
    .set(chargeE.AddMembersPage, true).toOption.get

  def allEmployersChargeEWithVersionAndStatus(version:Int,memberStatus:String): UserAnswers =
    allEmployersChargeE.set(chargeE.MemberAFTVersionPage(0),version).toOption.get
      .set(chargeE.MemberAFTVersionPage(1),1).toOption.get
      .set(chargeE.MemberStatusPage(0), memberStatus).toOption.get
      .set(chargeE.MemberStatusPage(1), memberStatus).toOption.get
}
