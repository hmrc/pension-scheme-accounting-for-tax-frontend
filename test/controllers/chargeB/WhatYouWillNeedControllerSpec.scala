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

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData._
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.UserAnswers
import org.mockito.Matchers.any
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.mockito.ArgumentCaptor
import org.mockito.Matchers
import pages.chargeB.WhatYouWillNeedPage
import play.api.Application
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

class WhatYouWillNeedControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers {
  private val userAnswers: Option[UserAnswers] = Some(SampleData.userAnswersWithSchemeNamePstrQuarter)
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build()
  private val templateToBeRendered = "chargeB/whatYouWillNeed.njk"
  private val jsonToPassToTemplate: JsObject =
    Json.obj(fields = "schemeName" -> SampleData.schemeName, "nextPage" -> SampleData.dummyCall.url)

  override def beforeEach: Unit = {
    super.beforeEach
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
  }

  private def httpPathGET: String = controllers.chargeB.routes.WhatYouWillNeedController.onPageLoad(srn, startDate).url

  "whatYouWillNeed Controller" must {
    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      when(mockCompoundNavigator.nextPage(Matchers.eq(WhatYouWillNeedPage), any(), any(), any(), any())).thenReturn(dummyCall)

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate)
    }
  }
}
