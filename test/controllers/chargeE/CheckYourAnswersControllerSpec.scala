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

package controllers.chargeE

import behaviours.CheckYourAnswersBehaviour
import controllers.base.ControllerSpecBase
import data.SampleData._
import helpers.CYAChargeEHelper
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.{UserAnswers, YearRange}
import pages.chargeE.{AnnualAllowanceYearPage, ChargeDetailsPage, CheckYourAnswersPage, MemberDetailsPage}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.DateHelper

import java.time.LocalDate

class CheckYourAnswersControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with CheckYourAnswersBehaviour {

  private val dynamicYearRange = YearRange("2019")

  private val templateToBeRendered = "check-your-answers.njk"

  private def httpGETRoute: String = controllers.chargeE.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt, 0).url
  private def httpOnClickRoute: String = controllers.chargeE.routes.CheckYourAnswersController.onClick(srn, startDate, accessType, versionInt, 0).url

  private def ua: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(MemberDetailsPage(0), memberDetails).toOption.get
    .set(AnnualAllowanceYearPage(0), dynamicYearRange).toOption.get
    .set(ChargeDetailsPage(0), chargeEDetails).toOption.get

  private val helper = new CYAChargeEHelper(srn, startDate, accessType, versionInt)
  private val rows = Seq(
    helper.chargeEMemberDetails(0, memberDetails),
    helper.chargeETaxYear(0, dynamicYearRange),
    helper.chargeEDetails(0, chargeEDetails)
  ).flatten

  private val jsonToPassToTemplate: JsObject = Json.obj(
    "list" -> rows
  )

  DateHelper.setDate(Some(LocalDate.of(2020, 4, 1)))

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

    behave like redirectToErrorOn5XX(
      httpPath = httpOnClickRoute,
      page = CheckYourAnswersPage,
      userAnswers = ua
    )
  }
}
