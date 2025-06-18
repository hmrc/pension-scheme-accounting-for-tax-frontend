/*
 * Copyright 2024 HM Revenue & Customs
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

package controllers.chargeC

import behaviours.CheckYourAnswersBehaviour
import controllers.base.ControllerSpecBase
import data.SampleData._
import helpers.CYAChargeCHelper
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.SponsoringEmployerType.{SponsoringEmployerTypeIndividual, SponsoringEmployerTypeOrganisation}
import models.UserAnswers
import pages.chargeC._
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryListRow

class CheckYourAnswersControllerSpec extends ControllerSpecBase with JsonMatchers with CheckYourAnswersBehaviour {

  private val index = 0
  private def httpGETRoute: String = controllers.chargeC.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt, index).url
  private def httpOnClickRoute: String = controllers.chargeC.routes.CheckYourAnswersController.onClick(srn, startDate, accessType, versionInt, index).url

  private def uaInd: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(ChargeCDetailsPage(index), chargeCDetails).toOption.get
    .set(WhichTypeOfSponsoringEmployerPage(index), SponsoringEmployerTypeIndividual).toOption.get
    .set(SponsoringIndividualDetailsPage(index), sponsoringIndividualDetails).toOption.get
    .set(SponsoringEmployerAddressPage(index), sponsoringEmployerAddress).toOption.get

  private def uaOrg: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(ChargeCDetailsPage(index), chargeCDetails).toOption.get
    .set(WhichTypeOfSponsoringEmployerPage(index), SponsoringEmployerTypeOrganisation).toOption.get
    .set(SponsoringOrganisationDetailsPage(index), sponsoringOrganisationDetails).toOption.get
    .set(SponsoringEmployerAddressPage(index), sponsoringEmployerAddress).toOption.get

  private def helper = new CYAChargeCHelper(srn, startDate, accessType, versionInt)
  
  private val answersInd: Seq[SummaryListRow] = Seq(
    Seq(helper.chargeCWhichTypeOfSponsoringEmployer(index, uaInd.get(WhichTypeOfSponsoringEmployerPage(index)).get)),
    helper.chargeCEmployerDetails(index, Left(sponsoringIndividualDetails)),
    Seq(helper.chargeCAddress(index, sponsoringEmployerAddress, Left(sponsoringIndividualDetails))),
    helper.chargeCChargeDetails(index, chargeCDetails)
  ).flatten

  private val answersOrg: Seq[SummaryListRow] = Seq(
    Seq(helper.chargeCWhichTypeOfSponsoringEmployer(index, uaOrg.get(WhichTypeOfSponsoringEmployerPage(index)).get)),
    helper.chargeCEmployerDetails(index, Right(sponsoringOrganisationDetails)),
    Seq(helper.chargeCAddress(index, sponsoringEmployerAddress, Right(sponsoringOrganisationDetails))),
    helper.chargeCChargeDetails(index, chargeCDetails)
  ).flatten


  "CheckYourAnswers Controller for individual" must {
    behave like cyaController(
      httpPath = httpGETRoute,
      chargeName = "chargeC",
      list = answersInd,
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
      submitUrl = httpOnClickRoute,
      userAnswers = uaInd
    )

    behave like controllerWithOnClick(
      httpPath = httpOnClickRoute,
      page = CheckYourAnswersPage,
      userAnswers = uaInd
    )
  }

  "CheckYourAnswers Controller for organisation" must {
    behave like cyaController(
      httpPath = httpGETRoute,
      chargeName = "chargeC",
      list = answersOrg,
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
      submitUrl = httpOnClickRoute,
      userAnswers = uaOrg
    )

    behave like controllerWithOnClick(
      httpPath = httpOnClickRoute,
      page = CheckYourAnswersPage,
      userAnswers = uaOrg
    )

    behave like redirectToErrorOn5XX(
      httpPath = httpOnClickRoute,
      page = CheckYourAnswersPage,
      userAnswers = uaOrg
    )
  }
}
