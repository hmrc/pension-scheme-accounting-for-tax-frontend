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
import data.SampleData._
import helpers.CheckYourAnswersHelper
import matchers.JsonMatchers
import models.UserAnswers
import pages.chargeG.{ChargeAmountsPage, ChargeDetailsPage, CheckYourAnswersPage, MemberDetailsPage}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.viewmodels.NunjucksSupport
import models.LocalDateBinder._

class CheckYourAnswersControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with CheckYourAnswersBehaviour {

  private val templateToBeRendered = "check-your-answers.njk"

  private def httpGETRoute: String = controllers.chargeG.routes.CheckYourAnswersController.onPageLoad(srn, startDate, 0).url
  private def httpOnClickRoute: String = controllers.chargeG.routes.CheckYourAnswersController.onClick(srn, startDate, 0).url

  private def ua: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(MemberDetailsPage(0), memberGDetails).toOption.get
    .set(ChargeDetailsPage(0), chargeGDetails).toOption.get
    .set(ChargeAmountsPage(0), chargeAmounts).toOption.get

  private val helper = new CheckYourAnswersHelper(ua, srn, startDate)
  private val rows = Seq(
    helper.chargeGMemberDetails(0, memberGDetails),
    helper.chargeGDetails(0, chargeGDetails),
    helper.chargeGAmounts(0, chargeAmounts)
  ).flatten

  private val jsonToPassToTemplate: JsObject = Json.obj(
    "list" -> rows
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
