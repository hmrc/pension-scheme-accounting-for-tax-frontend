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

package controllers.chargeG

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.requests.IdentifierRequest
import models.UserAnswers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mockito.ArgumentMatchers
import pages.chargeG.WhatYouWillNeedPage
import play.api.Application
import play.api.test.Helpers._
import play.twirl.api.Html
import views.html.chargeG.WhatYouWillNeedView

import scala.concurrent.Future

class WhatYouWillNeedControllerSpec extends ControllerSpecBase with JsonMatchers {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build()

  private def httpPathGET: String = controllers.chargeG.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, versionInt).url

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
  }

  private val userAnswers: Option[UserAnswers] = Some(userAnswersWithSchemeNamePstrQuarter)

  "whatYouWillNeed Controller" must {
    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(WhatYouWillNeedPage), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)

      val view = application.injector.instanceOf[WhatYouWillNeedView].apply(
        dummyCall.url,
        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
        schemeName
      )(httpGETRequest(httpPathGET), messages)

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }
  }
}
