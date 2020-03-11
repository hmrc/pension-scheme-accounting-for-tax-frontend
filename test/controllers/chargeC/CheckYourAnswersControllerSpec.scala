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

package controllers.chargeC

import behaviours.CheckYourAnswersBehaviour
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.UserAnswers
import pages.chargeC._
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.viewmodels.{NunjucksSupport, SummaryList}
import utils.CheckYourAnswersHelper
import models.LocalDateBinder._

class CheckYourAnswersControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with CheckYourAnswersBehaviour {

  private val templateToBeRendered = "check-your-answers.njk"
  private val index = 0
  private def httpGETRoute: String = controllers.chargeC.routes.CheckYourAnswersController.onPageLoad(srn, startDate, index).url
  private def httpOnClickRoute: String = controllers.chargeC.routes.CheckYourAnswersController.onClick(srn, startDate, index).url

  private def uaInd: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(ChargeCDetailsPage(index), chargeCDetails).toOption.get
    .set(WhichTypeOfSponsoringEmployerPage(index), true).toOption.get
    .set(SponsoringIndividualDetailsPage(index), sponsoringIndividualDetails).toOption.get
    .set(SponsoringEmployerAddressPage(index), sponsoringEmployerAddress).toOption.get

  private def uaOrg: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(ChargeCDetailsPage(index), chargeCDetails).toOption.get
    .set(WhichTypeOfSponsoringEmployerPage(index), false).toOption.get
    .set(SponsoringOrganisationDetailsPage(index), sponsoringOrganisationDetails).toOption.get
    .set(SponsoringEmployerAddressPage(index), sponsoringEmployerAddress).toOption.get

  private def helper(ua: UserAnswers) = new CheckYourAnswersHelper(ua, srn, startDate)
  
  private val answersInd: Seq[SummaryList.Row] = Seq(
    Seq(helper(uaInd).chargeCIsSponsoringEmployerIndividual(index, uaInd.get(WhichTypeOfSponsoringEmployerPage(index)).get)),
    helper(uaInd).chargeCEmployerDetails(index, Left(sponsoringIndividualDetails)),
    Seq(helper(uaInd).chargeCAddress(index, sponsoringEmployerAddress, Left(sponsoringIndividualDetails))),
    helper(uaInd).chargeCChargeDetails(index, chargeCDetails)
  ).flatten

  private val answersOrg: Seq[SummaryList.Row] = Seq(
    Seq(helper(uaOrg).chargeCIsSponsoringEmployerIndividual(index, uaOrg.get(WhichTypeOfSponsoringEmployerPage(index)).get)),
    helper(uaOrg).chargeCEmployerDetails(index, Right(sponsoringOrganisationDetails)),
    Seq(helper(uaOrg).chargeCAddress(index, sponsoringEmployerAddress, Right(sponsoringOrganisationDetails))),
    helper(uaOrg).chargeCChargeDetails(index, chargeCDetails)
  ).flatten

  private def jsonToPassToTemplate(answers: Seq[SummaryList.Row]): JsObject =
    Json.obj("list" -> Json.toJson(answers))

  "CheckYourAnswers Controller for individual" must {
    behave like cyaController(
      httpPath = httpGETRoute,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate(answersInd),
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
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate(answersOrg),
      userAnswers = uaOrg
    )

    behave like controllerWithOnClick(
      httpPath = httpOnClickRoute,
      page = CheckYourAnswersPage,
      userAnswers = uaOrg
    )
  }
}
