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
import data.SampleData._
import matchers.JsonMatchers
import models.UserAnswers
import pages.chargeF.ChargeDetailsPage
import pages.chargeF.CheckYourAnswersPage
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.CheckYourAnswersHelper
import models.LocalDateBinder._

class CheckYourAnswersControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with CheckYourAnswersBehaviour {

  private val templateToBeRendered = "check-your-answers.njk"
  private val helper = new CheckYourAnswersHelper(ua, srn, startDate)
  private val jsonToPassToTemplate: JsObject = Json.obj(
    "list" -> Seq(
      helper.chargeFDate(chargeFChargeDetails),
      helper.chargeFAmount(chargeFChargeDetails)
    ))

  private def httpGETRoute: String = controllers.chargeF.routes.CheckYourAnswersController.onPageLoad(srn, startDate).url

  private def httpOnClickRoute: String = controllers.chargeF.routes.CheckYourAnswersController.onClick(srn, startDate).url

  private def ua: UserAnswers =
    userAnswersWithSchemeNamePstrQuarter
      .set(ChargeDetailsPage, chargeFChargeDetails)
      .toOption
      .get

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
