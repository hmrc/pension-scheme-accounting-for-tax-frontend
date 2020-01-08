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

package controllers.chargeF

import behaviours.CheckYourAnswersBehaviour
import controllers.base.ControllerSpecBase
import data.SampleData
import matchers.JsonMatchers
import pages.chargeF.{ChargeDetailsPage, CheckYourAnswersPage}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.CheckYourAnswersHelper

class CheckYourAnswersControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with CheckYourAnswersBehaviour {

  private val templateToBeRendered = "check-your-answers.njk"

  private def httpGETRoute: String = controllers.chargeF.routes.CheckYourAnswersController.onPageLoad(SampleData.srn).url
  private def httpOnClickRoute: String = controllers.chargeF.routes.CheckYourAnswersController.onClick(SampleData.srn).url

  private def ua = SampleData.userAnswersWithSchemeName
    .set(ChargeDetailsPage, SampleData.chargeFChargeDetails).toOption.get

  private val helper = new CheckYourAnswersHelper(ua, SampleData.srn)

  private val jsonToPassToTemplate: JsObject = Json.obj(
    "list" -> Seq(
      helper.chargeFDate.get,
      helper.chargeFAmount.get
    ))

  "CheckYourAnswers Controller" must {
    behave like controllerWithGETNoSavedData(
      httpPath = httpGETRoute,
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
