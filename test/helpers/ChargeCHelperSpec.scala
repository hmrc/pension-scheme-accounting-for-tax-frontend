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

package helpers

import java.time.LocalDate

import base.SpecBase
import data.SampleData
import models.LocalDateBinder._
import models.SponsoringEmployerType.{SponsoringEmployerTypeIndividual, SponsoringEmployerTypeOrganisation}
import models.{Employer, MemberDetails, UserAnswers}
import pages.chargeC.{ChargeCDetailsPage, SponsoringIndividualDetailsPage, SponsoringOrganisationDetailsPage, WhichTypeOfSponsoringEmployerPage}
import utils.AFTConstants.QUARTER_START_DATE

import scala.collection.mutable.ArrayBuffer

class ChargeCHelperSpec extends SpecBase {

  val srn = "S1234567"
  val startDate: LocalDate = QUARTER_START_DATE
  val allEmployers: UserAnswers = UserAnswers()
    .set(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeIndividual).toOption.get
    .set(SponsoringIndividualDetailsPage(0), SampleData.sponsoringIndividualDetails).toOption.get
    .set(ChargeCDetailsPage(0), SampleData.chargeCDetails).toOption.get
    .set(WhichTypeOfSponsoringEmployerPage(1), SponsoringEmployerTypeOrganisation).toOption.get
    .set(SponsoringOrganisationDetailsPage(1), SampleData.sponsoringOrganisationDetails).toOption.get
    .set(ChargeCDetailsPage(1), SampleData.chargeCDetails).toOption.get

  val allEmployersIncludingDeleted: UserAnswers = allEmployers
    .set(WhichTypeOfSponsoringEmployerPage(2), SponsoringEmployerTypeIndividual).toOption.get
    .set(SponsoringIndividualDetailsPage(2), SampleData.memberDetailsDeleted).toOption.get
    .set(ChargeCDetailsPage(2), SampleData.chargeCDetails).toOption.get

  def viewLink(index: Int): String = controllers.chargeC.routes.CheckYourAnswersController.onPageLoad(srn, startDate, index).url
  def removeLink(index: Int): String = controllers.chargeC.routes.DeleteEmployerController.onPageLoad(srn, startDate, index).url
  def expectedEmployer(memberDetails: MemberDetails, index: Int, nino:Option[String] = Some("CS121212C")): Employer =
    Employer(index, memberDetails.fullName, nino, SampleData.chargeAmount1, viewLink(index), removeLink(index), memberDetails.isDeleted)

  def expectedAllEmployers: Seq[Employer] = ArrayBuffer(
    expectedEmployer(SampleData.sponsoringIndividualDetails, 0),
    Employer(1,
      SampleData.sponsoringOrganisationDetails.name,
      None,
      SampleData.chargeAmount1,
      viewLink(1), removeLink(1))
  )


  def expectedEmployersIncludingDeleted: Seq[Employer] = expectedAllEmployers ++ Seq(
    expectedEmployer(SampleData.memberDetailsDeleted, 2, nino = Some("AB123456C"))
  )

  ".getOverseasTransferEmployers" must {
    "return all the members added in charge G" in {
      ChargeCHelper.getSponsoringEmployers(allEmployers, srn, startDate) mustBe expectedAllEmployers
    }
  }

  ".getOverseasTransferEmployersIncludingDeleted" must {
    "return all the members added in charge G" in {
      ChargeCHelper.getSponsoringEmployersIncludingDeleted(allEmployersIncludingDeleted, srn, startDate) mustBe expectedEmployersIncludingDeleted
    }
  }

}
