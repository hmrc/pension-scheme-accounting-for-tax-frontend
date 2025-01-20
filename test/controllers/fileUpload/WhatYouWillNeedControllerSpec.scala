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

package controllers.fileUpload

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.ChargeType.{ChargeTypeAnnualAllowance, ChargeTypeLifetimeAllowance}
import models.LocalDateBinder._
import models.requests.IdentifierRequest
import models.{ChargeType, UserAnswers}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import pages.IsPublicServicePensionsRemedyPage
import pages.fileUpload.WhatYouWillNeedPage
import play.api.Application
import play.api.test.FakeRequest
import play.api.test.Helpers._
import views.html.fileUpload.WhatYouWillNeedView

class WhatYouWillNeedControllerSpec extends ControllerSpecBase with JsonMatchers {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build()
  private val chargeType = ChargeType.ChargeTypeAnnualAllowance
  private val uaPsrTrue: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .setOrException(IsPublicServicePensionsRemedyPage(ChargeTypeAnnualAllowance,None), true)
  private val uaPsrFalse: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .setOrException(IsPublicServicePensionsRemedyPage(ChargeTypeLifetimeAllowance,None), false)

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
  }

  "whatYouWillNeed Controller" must {
    "return OK and the correct view for a GET without paragraph for PublicServicePensionsRemedy" in {
      val request = FakeRequest(GET, controllers.fileUpload.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, versionInt, chargeType).url)
      mutableFakeDataRetrievalAction.setDataToReturn(Some(uaPsrFalse))

      when(mockCompoundNavigator
        .nextPage(ArgumentMatchers.eq(WhatYouWillNeedPage(chargeType)), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)

      val submitUrl = dummyCall.url
      val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url
      val (templateDownloadLink, instructionsDownloadLink) =
        (controllers.routes.FileDownloadController.templateFile(chargeType, None).url,
          controllers.routes.FileDownloadController.instructionsFile(chargeType, None).url)

      val view = application.injector.instanceOf[WhatYouWillNeedView].apply(
        chargeType.toString, ChargeType.fileUploadText(chargeType), submitUrl, schemeName, returnUrl, false,
        templateDownloadLink, instructionsDownloadLink)(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }
    "return OK and the correct view for a GET with paragraph for PublicServicePensionsRemedy" in {
      val request = FakeRequest(GET, controllers.fileUpload.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, versionInt, chargeType).url)
      mutableFakeDataRetrievalAction.setDataToReturn(Some(uaPsrTrue))
      when(mockCompoundNavigator
        .nextPage(ArgumentMatchers.eq(WhatYouWillNeedPage(chargeType)), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)

      val submitUrl = dummyCall.url
      val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url
      val (templateDownloadLink, instructionsDownloadLink) =
        (controllers.routes.FileDownloadController.templateFile(chargeType, Some(true)).url,
          controllers.routes.FileDownloadController.instructionsFile(chargeType, Some(true)).url)

      val view = application.injector.instanceOf[WhatYouWillNeedView].apply(
        chargeType.toString, ChargeType.fileUploadText(chargeType), submitUrl, schemeName, returnUrl, true,
        templateDownloadLink, instructionsDownloadLink)(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }
  }
}
