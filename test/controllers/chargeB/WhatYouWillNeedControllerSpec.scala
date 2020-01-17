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

package controllers.chargeB

import behaviours.ControllerBehaviours
import controllers.base.ControllerSpecBase
import data.SampleData
import matchers.JsonMatchers
import models.UserAnswers
import pages.chargeB.WhatYouWillNeedPage
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.viewmodels.NunjucksSupport

class WhatYouWillNeedControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with ControllerBehaviours {
  private val templateToBeRendered = "chargeB/whatYouWillNeed.njk"
  private def httpPathGET: String = controllers.chargeB.routes.WhatYouWillNeedController.onPageLoad(SampleData.srn).url

  private val jsonToPassToTemplate:JsObject = Json.obj(
    fields = "schemeName" -> SampleData.schemeName, "nextPage" -> SampleData.dummyCall.url)

  private val userAnswers: Option[UserAnswers] = Some(SampleData.userAnswersWithSchemeName)

  "whatYouWillNeed Controller" must {
    behave like controllerWithGETNoSavedData(
      httpPath = httpPathGET,
      page = WhatYouWillNeedPage,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate,
      userAnswers
    )
  }
}
