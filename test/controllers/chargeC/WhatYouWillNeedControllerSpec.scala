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

package controllers.chargeC

import controllers.base.ControllerSpecBase
import data.SampleData
import matchers.JsonMatchers
import models.{GenericViewModel, UserAnswers}
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, Matchers}
import pages.chargeC.WhatYouWillNeedPage
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

class WhatYouWillNeedControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers {
  private val templateToBeRendered = "chargeC/whatYouWillNeed.njk"
  private def httpPathGET: String = controllers.chargeC.routes.WhatYouWillNeedController.onPageLoad(SampleData.srn).url

  private val jsonToPassToTemplate = Json.obj(
    "viewModel" -> GenericViewModel(
      submitUrl = SampleData.dummyCall.url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(SampleData.srn),
      schemeName = SampleData.schemeName)
  )

  override def beforeEach: Unit = {
    super.beforeEach
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
  }

  private val userAnswers: Option[UserAnswers] = Some(SampleData.userAnswersWithSchemeName)

  "whatYouWillNeed Controller" must {
    "return OK and the correct view for a GET" in {
      val application = applicationBuilder(userAnswers = userAnswers).build()
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      when(mockCompoundNavigator.nextPage(Matchers.eq(WhatYouWillNeedPage), any(), any(), any())).thenReturn(SampleData.dummyCall)

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate)

      application.stop()
    }
  }
}
