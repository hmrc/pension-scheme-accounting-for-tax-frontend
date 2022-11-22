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
import data.SampleData
import data.SampleData.{accessType, versionInt}
import helpers.FormatHelper
import models.AmendedChargeStatus.{Added, Updated}
import models.ChargeType.ChargeTypeAuthSurplus
import models.LocalDateBinder._
import models.SponsoringEmployerType.{SponsoringEmployerTypeIndividual, SponsoringEmployerTypeOrganisation}
import models.viewModels.ViewAmendmentDetails
import models.{AmendedChargeStatus, Employer, MemberDetails, UserAnswers}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeC._
import utils.AFTConstants.QUARTER_START_DATE

import java.time.LocalDate
import scala.collection.mutable.ArrayBuffer

class ChargeCServiceSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach {

  val srn = "S1234567"
  val startDate: LocalDate = QUARTER_START_DATE

  val oneEmployerLastCharge: UserAnswers = UserAnswers()
    .set(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeIndividual).toOption.get
    .set(SponsoringIndividualDetailsPage(0), SampleData.sponsoringIndividualDetails).toOption.get
    .set(ChargeCDetailsPage(0), SampleData.chargeCDetails).toOption.get

  val allEmployers: UserAnswers = UserAnswers()
    .set(MemberAFTVersionPage(0), SampleData.version.toInt).toOption.get
    .set(MemberStatusPage(0), AmendedChargeStatus.Added.toString).toOption.get
    .set(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeIndividual).toOption.get
    .set(SponsoringIndividualDetailsPage(0), SampleData.sponsoringIndividualDetails).toOption.get
    .set(ChargeCDetailsPage(0), SampleData.chargeCDetails).toOption.get
    .set(MemberAFTVersionPage(1), SampleData.version.toInt).toOption.get
    .set(MemberStatusPage(1), AmendedChargeStatus.Updated.toString).toOption.get
    .set(WhichTypeOfSponsoringEmployerPage(1), SponsoringEmployerTypeOrganisation).toOption.get
    .set(SponsoringOrganisationDetailsPage(1), SampleData.sponsoringOrganisationDetails).toOption.get
    .set(ChargeCDetailsPage(1), SampleData.chargeCDetails).toOption.get

  def viewLink(index: Int): String = controllers.chargeC.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt, index).url

  def removeLink(index: Int): String = controllers.chargeC.routes.DeleteEmployerController.onPageLoad(srn, startDate, accessType, versionInt, index).url

  def lastChargeLink(index: Int): String = controllers.chargeC.routes.RemoveLastChargeController.onPageLoad(srn, startDate, accessType, versionInt, index).url

  def expectedEmployer(memberDetails: MemberDetails, index: Int): Employer =
    Employer(index, memberDetails.fullName, SampleData.chargeAmount1, viewLink(index), removeLink(index))

  def expectedLastChargeEmployer: Seq[Employer] =
    ArrayBuffer(Employer(0, "First Last", SampleData.chargeAmount1, viewLink(0), lastChargeLink(0))).toSeq

  def expectedAllEmployers: Seq[Employer] = ArrayBuffer(
    expectedEmployer(SampleData.sponsoringIndividualDetails, 0),
    Employer(1,
      SampleData.sponsoringOrganisationDetails.name,
      SampleData.chargeAmount1,
      viewLink(1), removeLink(1))
  ).toSeq

  val chargeCHelper: ChargeCService = new ChargeCService()

  "getAllAuthSurplusAmendments" must {
    "return all the amendments for auth surplus charge" in {
      val expectedAmendments = Seq(
        ViewAmendmentDetails(
          SampleData.sponsoringIndividualDetails.fullName, ChargeTypeAuthSurplus.toString,
          FormatHelper.formatCurrencyAmountAsString(SampleData.chargeCDetails.amountTaxDue),
          Added
        ),
        ViewAmendmentDetails(
          SampleData.sponsoringOrganisationDetails.name, ChargeTypeAuthSurplus.toString,
          FormatHelper.formatCurrencyAmountAsString(SampleData.chargeCDetails.amountTaxDue),
          Updated
        )
      )
      chargeCHelper.getAllAuthSurplusAmendments(allEmployers, versionInt) mustBe expectedAmendments
    }
  }
}
