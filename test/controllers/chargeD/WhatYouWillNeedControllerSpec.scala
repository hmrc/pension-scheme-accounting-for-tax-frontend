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

import behaviours.ControllerBehaviours
import controllers.base.ControllerSpecBase
import data.SampleData
import matchers.JsonMatchers
import models.GenericViewModel
import pages.chargeD.WhatYouWillNeedPage
import play.api.libs.json.Json
import uk.gov.hmrc.viewmodels.NunjucksSupport

class WhatYouWillNeedControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with ControllerBehaviours {
  private val templateToBeRendered = "chargeD/whatYouWillNeed.njk"
  private def httpGETRoute: String = controllers.chargeD.routes.WhatYouWillNeedController.onPageLoad(SampleData.srn).url

  private val jsonToPassToTemplate = Json.obj(
    "viewModel" -> GenericViewModel(
      submitUrl = SampleData.dummyCall.url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(SampleData.srn),
      schemeName = SampleData.schemeName)
  )

  "whatYouWillNeed Controller" must {
    behave like controllerWithGETNoSavedData(
      httpPath = httpGETRoute,
      page = WhatYouWillNeedPage,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate
    )
  }
}
