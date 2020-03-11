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

import java.time.LocalDate

import base.SpecBase
import data.SampleData
import models.{Employer, MemberDetails, UserAnswers}
import pages.chargeC.{
  ChargeCDetailsPage,
  IsSponsoringEmployerIndividualPage,
  SponsoringIndividualDetailsPage,
  SponsoringOrganisationDetailsPage
}

import scala.collection.mutable.ArrayBuffer
import utils.AFTConstants.QUARTER_START_DATE
import models.LocalDateBinder._

class ChargeCServiceSpec extends SpecBase {

  val srn = "S1234567"
  val startDate: LocalDate = QUARTER_START_DATE

  val allEmployers: UserAnswers = UserAnswers()
    .set(IsSponsoringEmployerIndividualPage(0), true)
    .toOption
    .get
    .set(SponsoringIndividualDetailsPage(0), SampleData.sponsoringIndividualDetails)
    .toOption
    .get
    .set(ChargeCDetailsPage(0), SampleData.chargeCDetails)
    .toOption
    .get
    .set(IsSponsoringEmployerIndividualPage(1), false)
    .toOption
    .get
    .set(SponsoringOrganisationDetailsPage(1), SampleData.sponsoringOrganisationDetails)
    .toOption
    .get
    .set(ChargeCDetailsPage(1), SampleData.chargeCDetails)
    .toOption
    .get

  val allEmployersIncludingDeleted: UserAnswers = allEmployers
    .set(IsSponsoringEmployerIndividualPage(2), true)
    .toOption
    .get
    .set(SponsoringIndividualDetailsPage(2), SampleData.memberDetailsDeleted)
    .toOption
    .get
    .set(ChargeCDetailsPage(2), SampleData.chargeCDetails)
    .toOption
    .get

  def viewLink(index: Int): String = controllers.chargeC.routes.CheckYourAnswersController.onPageLoad(srn, startDate, index).url
  def removeLink(index: Int): String = controllers.chargeC.routes.DeleteEmployerController.onPageLoad(srn, startDate, index).url

  def expectedEmployer(memberDetails: MemberDetails, index: Int): Employer =
    Employer(index, memberDetails.fullName, SampleData.chargeAmount1, viewLink(index), removeLink(index), memberDetails.isDeleted)

  def expectedAllEmployers: Seq[Employer] = ArrayBuffer(
    expectedEmployer(SampleData.sponsoringIndividualDetails, 0),
    Employer(1, SampleData.sponsoringOrganisationDetails.name, SampleData.chargeAmount1, viewLink(1), removeLink(1))
  )

  def expectedEmployersIncludingDeleted: Seq[Employer] = expectedAllEmployers ++ Seq(
    expectedEmployer(SampleData.memberDetailsDeleted, 2)
  )

  ".getOverseasTransferEmployers" must {
    "return all the members added in charge G" in {
      ChargeCService.getSponsoringEmployers(allEmployers, srn, startDate) mustBe expectedAllEmployers
    }
  }

  ".getOverseasTransferEmployersIncludingDeleted" must {
    "return all the members added in charge G" in {
      ChargeCService.getSponsoringEmployersIncludingDeleted(allEmployersIncludingDeleted, srn, startDate) mustBe expectedEmployersIncludingDeleted
    }
  }

}
