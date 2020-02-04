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
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.CheckYourAnswersHelper

class CheckYourAnswersControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with CheckYourAnswersBehaviour {

  private val templateToBeRendered = "check-your-answers.njk"
  private val index = 0
  private def httpGETRoute: String = controllers.chargeC.routes.CheckYourAnswersController.onPageLoad(srn, index).url
  private def httpOnClickRoute: String = controllers.chargeC.routes.CheckYourAnswersController.onClick(srn, index).url

  private def ua: UserAnswers = userAnswersWithSchemeName
    .set(ChargeCDetailsPage(index), chargeCDetails).toOption.get
    .set(IsSponsoringEmployerIndividualPage(index), true).toOption.get
    .set(SponsoringIndividualDetailsPage(index), sponsoringIndividualDetails).toOption.get
    .set(SponsoringEmployerAddressPage(index), sponsoringEmployerAddress).toOption.get

  private val helper = new CheckYourAnswersHelper(ua, srn)
  private val answers = Seq(
    Seq(helper.chargeCIsSponsoringEmployerIndividual(index, true)),
    helper.chargeCEmployerDetails(index, Left(sponsoringIndividualDetails)),
    Seq(helper.chargeCAddress(index, sponsoringEmployerAddress)),
    helper.chargeCChargeDetails(index, chargeCDetails)
  ).flatten
  private val jsonToPassToTemplate: JsObject = Json.obj(
    "list" -> Json.toJson(answers)
  )

  "CheckYourAnswers Controller" must {
    behave like cyaController(
      httpPath = httpGETRoute,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate,
      userAnswers = ua
    )

    behave like controllerWithOnClick(
      httpPath = httpOnClickRoute,
      page = CheckYourAnswersPage
    )
  }
}
