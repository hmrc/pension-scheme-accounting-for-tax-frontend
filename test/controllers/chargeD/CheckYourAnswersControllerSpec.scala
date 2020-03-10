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

package controllers.chargeD

import behaviours.CheckYourAnswersBehaviour
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.UserAnswers
import pages.chargeD.ChargeDetailsPage
import pages.chargeD.CheckYourAnswersPage
import pages.chargeD.MemberDetailsPage
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.CheckYourAnswersHelper
import models.LocalDateBinder._

class CheckYourAnswersControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with CheckYourAnswersBehaviour {

  private val templateToBeRendered = "check-your-answers.njk"
  private val helper = new CheckYourAnswersHelper(ua, srn, startDate)
  private val rows = Seq(
    helper.chargeDMemberDetails(0, memberDetails),
    helper.chargeDDetails(0, chargeDDetails),
    Seq(helper.total(chargeAmount1 + chargeAmount2))
  ).flatten
  private val jsonToPassToTemplate: JsObject = Json.obj(
    "list" -> rows
  )

  private def httpGETRoute: String = controllers.chargeD.routes.CheckYourAnswersController.onPageLoad(srn, startDate, 0).url

  private def httpOnClickRoute: String = controllers.chargeD.routes.CheckYourAnswersController.onClick(srn, startDate, 0).url

  private def ua: UserAnswers =
    userAnswersWithSchemeNamePstrQuarter
      .set(MemberDetailsPage(0), memberDetails)
      .toOption
      .get
      .set(ChargeDetailsPage(0), chargeDDetails)
      .toOption
      .get

  "CheckYourAnswers Controller" must {
    behave like cyaController(
      httpPath = httpGETRoute,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate,
      userAnswers = ua
    )

    "CheckYourAnswers Controller with both rates of tax set" must {
      behave like controllerWithOnClick(
        httpPath = httpOnClickRoute,
        page = CheckYourAnswersPage,
        userAnswers = ua
      )
    }

    "CheckYourAnswers Controller with no 25% rate of tax set" must {
      behave like controllerWithOnClick(
        httpPath = httpOnClickRoute,
        page = CheckYourAnswersPage,
        userAnswers = ua.set(ChargeDetailsPage(0), chargeDDetails.copy(taxAt25Percent = None)).get
      )
    }

    "CheckYourAnswers Controller with no 55% rate of tax set" must {
      behave like controllerWithOnClick(
        httpPath = httpOnClickRoute,
        page = CheckYourAnswersPage,
        userAnswers = ua.set(ChargeDetailsPage(0), chargeDDetails.copy(taxAt55Percent = None)).get
      )
    }
  }
}
