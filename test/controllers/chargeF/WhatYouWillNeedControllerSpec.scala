/*
 * Copyright 2024 HM Revenue & Customs
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

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData._
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.UserAnswers
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import pages.chargeF.WhatYouWillNeedPage
import play.api.Application
import play.api.test.Helpers._
import utils.AFTConstants.QUARTER_START_DATE
import views.html.chargeF.WhatYouWillNeedView

class WhatYouWillNeedControllerSpec extends ControllerSpecBase with JsonMatchers {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = registerApp(applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build())

  private def httpPathGET: String = controllers.chargeF.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, versionInt).url

  private val userAnswers: Option[UserAnswers] = Some(SampleData.userAnswersWithSchemeNamePstrQuarter)

  "whatYouWillNeed Controller" must {
    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(WhatYouWillNeedPage), any(), any(), any(), any(), any(), any())(any()))
        .thenReturn(SampleData.dummyCall)

      val request = httpGETRequest(httpPathGET)
      val result = route(application, request).value

      val view = application.injector.instanceOf[WhatYouWillNeedView].apply(
        SampleData.dummyCall.url,
        SampleData.schemeName,
        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, QUARTER_START_DATE, accessType, versionInt).url
      )(request, messages)

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }
  }
}
