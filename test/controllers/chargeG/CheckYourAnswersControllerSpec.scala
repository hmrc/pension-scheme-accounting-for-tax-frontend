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

package controllers.chargeG

import behaviours.CheckYourAnswersBehaviour
import controllers.base.ControllerSpecBase
import data.SampleData
import matchers.JsonMatchers
import pages.chargeG.{ChargeAmountsPage, ChargeDetailsPage, CheckYourAnswersPage, MemberDetailsPage}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.CheckYourAnswersHelper

class CheckYourAnswersControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with CheckYourAnswersBehaviour {

  private val templateToBeRendered = "check-your-answers.njk"

  private def httpPathGET: String = controllers.chargeG.routes.CheckYourAnswersController.onPageLoad(SampleData.srn, 0).url
  private def httpOnClickRoute: String = controllers.chargeG.routes.CheckYourAnswersController.onClick(SampleData.srn, 0).url

  private def ua = SampleData.userAnswersWithSchemeName
    .set(MemberDetailsPage(0), SampleData.memberGDetails).toOption.get
    .set(ChargeDetailsPage(0), SampleData.chargeGDetails).toOption.get
    .set(ChargeAmountsPage(0), SampleData.chargeAmounts).toOption.get

  private val helper = new CheckYourAnswersHelper(ua, SampleData.srn)
  private val rows = Seq(
    helper.chargeGMemberDetails(0).get,
    helper.chargeGDetails(0).get,
    helper.chargeGAmounts(0).get
  ).flatten

  private val jsonToPassToTemplate: JsObject = Json.obj(
    "list" -> rows
  )

  "CheckYourAnswers Controller" must {
    behave like controllerWithGETNoSavedData(
      httpPath = httpPathGET,
      page = CheckYourAnswersPage,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate,
      userAnswers = Some(ua)
    )

    behave like controllerWithOnClick(
      httpPath = httpOnClickRoute,
      page = CheckYourAnswersPage
    )
  }
}
