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
import models.requests.DataRequest
import models.{Employer, MemberDetails, SessionAccessData, UserAnswers}
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeC.{ChargeCDetailsPage, SponsoringIndividualDetailsPage, SponsoringOrganisationDetailsPage, WhichTypeOfSponsoringEmployerPage}
import play.api.mvc.AnyContent
import uk.gov.hmrc.domain.PsaId
import utils.AFTConstants.QUARTER_START_DATE
import utils.DeleteChargeHelper

import scala.collection.mutable.ArrayBuffer

class ChargeCHelperSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach {

  val srn = "S1234567"
  val startDate: LocalDate = QUARTER_START_DATE

  val oneEmployerLastCharge: UserAnswers = UserAnswers()
    .set(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeIndividual).toOption.get
    .set(SponsoringIndividualDetailsPage(0), SampleData.sponsoringIndividualDetails).toOption.get
    .set(ChargeCDetailsPage(0), SampleData.chargeCDetails).toOption.get

  val allEmployers: UserAnswers = oneEmployerLastCharge
    .set(WhichTypeOfSponsoringEmployerPage(1), SponsoringEmployerTypeOrganisation).toOption.get
    .set(SponsoringOrganisationDetailsPage(1), SampleData.sponsoringOrganisationDetails).toOption.get
    .set(ChargeCDetailsPage(1), SampleData.chargeCDetails).toOption.get

  val allEmployersIncludingDeleted: UserAnswers = allEmployers
    .set(WhichTypeOfSponsoringEmployerPage(2), SponsoringEmployerTypeIndividual).toOption.get
    .set(SponsoringIndividualDetailsPage(2), SampleData.memberDetailsDeleted).toOption.get
    .set(ChargeCDetailsPage(2), SampleData.chargeCDetails).toOption.get

  def viewLink(index: Int): String = controllers.chargeC.routes.CheckYourAnswersController.onPageLoad(srn, startDate, index).url
  def removeLink(index: Int): String = controllers.chargeC.routes.DeleteEmployerController.onPageLoad(srn, startDate, index).url
  def lastChargeLink(index: Int): String = controllers.chargeC.routes.RemoveLastChargeController.onPageLoad(srn, startDate, index).url
  def expectedEmployer(memberDetails: MemberDetails, index: Int): Employer =
    Employer(index, memberDetails.fullName, SampleData.chargeAmount1, viewLink(index), removeLink(index), memberDetails.isDeleted)

  def expectedLastChargeEmployer: Seq[Employer] =
    ArrayBuffer(Employer(0, "First Last", SampleData.chargeAmount1, viewLink(0), lastChargeLink(0)))

  def expectedAllEmployers: Seq[Employer] = ArrayBuffer(
    expectedEmployer(SampleData.sponsoringIndividualDetails, 0),
    Employer(1,
      SampleData.sponsoringOrganisationDetails.name,
      SampleData.chargeAmount1,
      viewLink(1), removeLink(1))
  )

  def expectedEmployersIncludingDeleted: Seq[Employer] = expectedAllEmployers ++ Seq(
    expectedEmployer(SampleData.memberDetailsDeleted, 2)
  )

  val mockDeleteChargeHelper: DeleteChargeHelper = mock[DeleteChargeHelper]
  val chargeCHelper: ChargeCHelper = new ChargeCHelper(mockDeleteChargeHelper)

  private def dataRequest(ua: UserAnswers = UserAnswers()): DataRequest[AnyContent] =
    DataRequest(fakeRequest, "", PsaId(SampleData.psaId), ua,
      SampleData.sessionData(name = None, sessionAccessData = SampleData.sessionAccessData(2)))

  override def beforeEach: Unit = {
    reset(mockDeleteChargeHelper)
    when(mockDeleteChargeHelper.isLastCharge(any())).thenReturn(false)
  }

  ".getSponsoringEmployers" must {
    "return all the members added in charge C when it is not the last charge" in {
      chargeCHelper.getSponsoringEmployers(allEmployers, srn, startDate)(request()) mustBe expectedAllEmployers
    }

    "return all the members added in charge C when it is the last charge" in {
      when(mockDeleteChargeHelper.isLastCharge(any())).thenReturn(true)
      chargeCHelper.getSponsoringEmployers(oneEmployerLastCharge, srn, startDate)(dataRequest()) mustBe expectedLastChargeEmployer
    }
  }

  ".getOverseasTransferEmployersIncludingDeleted" must {
    "return all the members added in charge C" in {
      chargeCHelper.getSponsoringEmployersIncludingDeleted(allEmployersIncludingDeleted, srn, startDate)(request()) mustBe expectedEmployersIncludingDeleted
    }
  }

}
