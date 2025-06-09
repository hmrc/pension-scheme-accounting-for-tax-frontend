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

package controllers.chargeD

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData._
import matchers.JsonMatchers
import models.ChargeType.ChargeTypeLifetimeAllowance
import models.LocalDateBinder._
import models.requests.IdentifierRequest
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import pages.IsPublicServicePensionsRemedyPage
import pages.chargeD.WhatYouWillNeedPage
import play.api.Application
import play.api.test.Helpers._
import utils.AFTConstants.QUARTER_START_DATE
import views.html.chargeD.WhatYouWillNeedView

class WhatYouWillNeedControllerSpec extends ControllerSpecBase with JsonMatchers {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = registerApp(applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build())

  private val uaPsrTrue = userAnswersWithSchemeNamePstrQuarter.setOrException(IsPublicServicePensionsRemedyPage(ChargeTypeLifetimeAllowance, Some(0)), true)
  private val uaPsrFalse = userAnswersWithSchemeNamePstrQuarter.setOrException(IsPublicServicePensionsRemedyPage(ChargeTypeLifetimeAllowance, Some(0)), false)
  private def httpPathGET: String = controllers.chargeD.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, versionInt, index = 0).url

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
  }

  "whatYouWillNeed Controller" must {

    "return OK and the correct view for a GET without extra PSR paragraph" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(uaPsrFalse))
      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(WhatYouWillNeedPage), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)
      val request = httpGETRequest(httpPathGET)
      val result = route(application, request).value

      val view = application.injector.instanceOf[WhatYouWillNeedView].apply(
        dummyCall.url,
        SampleData.schemeName,
        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, QUARTER_START_DATE, accessType, versionInt).url,
        None,
      )(request, messages)

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }
    "return OK and the correct view for a GET with extra PSR paragraph" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(uaPsrTrue))
      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(WhatYouWillNeedPage), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)
      val request = httpGETRequest(httpPathGET)
      val result = route(application, request).value

      val view = application.injector.instanceOf[WhatYouWillNeedView].apply(
        dummyCall.url,
        SampleData.schemeName,
        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, QUARTER_START_DATE, accessType, versionInt).url,
        Some("chargeD.whatYouWillNeed.li6"),
      )(request, messages)

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }
  }
}
