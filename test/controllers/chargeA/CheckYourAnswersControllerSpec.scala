/*
 * Copyright 2019 HM Revenue & Customs
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

package controllers.chargeA

import behaviours.ControllerBehaviours
import controllers.base.ControllerSpecBase
import data.SampleData
import matchers.JsonMatchers
import models.GenericViewModel
import pages.chargeA.{ChargeDetailsPage, WhatYouWillNeedPage}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.viewmodels.NunjucksSupport
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import utils.CheckYourAnswersHelper

class CheckYourAnswersControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with ControllerBehaviours {

  private val templateToBeRendered = "chargeA/check-your-answers.njk"

  private def httpGETRoute: String = controllers.chargeA.routes.CheckYourAnswersController.onPageLoad(SampleData.srn).url

  private def ua = SampleData.userAnswersWithSchemeName
    .set(ChargeDetailsPage, SampleData.chargeAChargeDetails).toOption.get

  private val helper = new CheckYourAnswersHelper(ua, SampleData.srn)

  private val jsonToPassToTemplate: JsObject = Json.obj(
    "list" -> Seq(
      helper.chargeAMembers.get,
      helper.chargeAAmountLowerRate.get,
      helper.chargeAAmountHigherRate.get,
      Row(Key(msg"total", classes = Seq("govuk-!-width-one-half", "newclass")),
        value = Value(
          Literal(
            CheckYourAnswersHelper.formatBigDecimalAsString(ua.get(ChargeDetailsPage).map(_.totalAmount).getOrElse(BigDecimal(0)))
          )
        )
      )
    ),
    "viewModel" -> GenericViewModel(
      submitUrl = routes.CheckYourAnswersController.onClick(SampleData.srn).url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(SampleData.srn),
      schemeName = SampleData.schemeName))

  "CheckYourAnswers Controller" must {
    behave like controllerWithGET(
      httpPath = httpGETRoute,
      page = WhatYouWillNeedPage,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate,
      userAnswers = Some(ua)
    )
  }
}
