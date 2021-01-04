/*
 * Copyright 2021 HM Revenue & Customs
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

package controllers.chargeB

import behaviours.CheckYourAnswersBehaviour
import controllers.base.ControllerSpecBase
import data.SampleData._
import helpers.CYAChargeBHelper
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.UserAnswers
import pages.chargeB.{ChargeBDetailsPage, CheckYourAnswersPage}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.viewmodels.NunjucksSupport

class CheckYourAnswersControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with CheckYourAnswersBehaviour {

  private val templateToBeRendered = "check-your-answers.njk"

  private def httpGETRoute: String = controllers.chargeB.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt).url
  private def httpOnClickRoute: String = controllers.chargeB.routes.CheckYourAnswersController.onClick(srn, startDate, accessType, versionInt).url

  private def ua: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(ChargeBDetailsPage, chargeBDetails).toOption.get

  private val helper = new CYAChargeBHelper(srn, startDate, accessType, versionInt)

  private val jsonToPassToTemplate: JsObject = Json.obj(
    "list" -> helper.chargeBDetails(chargeBDetails)
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
      page = CheckYourAnswersPage,
      userAnswers = ua
    )
  }
}
