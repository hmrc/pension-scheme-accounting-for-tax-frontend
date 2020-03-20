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

package controllers.chargeE

import java.time.LocalDate

import behaviours.CheckYourAnswersBehaviour
import connectors.AFTConnector
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.DynamicYearRange
import models.{YearRange, UserAnswers}
import pages.chargeE.{CheckYourAnswersPage, ChargeDetailsPage, AnnualAllowanceYearPage, MemberDetailsPage}
import play.api.libs.json.{Json, JsObject}
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.CheckYourAnswersHelper
import models.LocalDateBinder._
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import play.api.Application
import play.twirl.api.Html
import utils.DateHelper

import scala.concurrent.Future

class CheckYourAnswersControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with CheckYourAnswersBehaviour {

  private val dynamicYearRange = DynamicYearRange("2019")

  private val templateToBeRendered = "check-your-answers.njk"

  private def httpGETRoute: String = controllers.chargeE.routes.CheckYourAnswersController.onPageLoad(srn, startDate, 0).url
  private def httpOnClickRoute: String = controllers.chargeE.routes.CheckYourAnswersController.onClick(srn, startDate, 0).url

  private def ua: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(MemberDetailsPage(0), memberDetails).toOption.get
    .set(AnnualAllowanceYearPage(0), dynamicYearRange).toOption.get
    .set(ChargeDetailsPage(0), chargeEDetails).toOption.get

  private val helper = new CheckYourAnswersHelper(ua, srn, startDate)
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
  }
}
