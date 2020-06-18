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

package controllers

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData.{dummyCall, userAnswersWithSchemeNamePstrQuarter, userAnswersWithSchemeName}
import models.LocalDateBinder._
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html

import scala.concurrent.Future

class CannotChangeAFTReturnControllerSpec extends ControllerSpecBase {

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build()

  "CannotChangeAFTReturn Controller" must {

    "must return OK and the correct view for a GET" in {
      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(dummyCall.url)

      val request = FakeRequest(GET, routes.CannotChangeAFTReturnController.onPageLoad(SampleData.srn, SampleData.startDate, SampleData.accessType, SampleData.versionInt).url)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter))

      val result = route(application, request).value

      status(result) mustEqual OK

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), any())(any())

      templateCaptor.getValue mustEqual "cannot-change-aft-return.njk"
    }
  }

  "redirect to session expired page when there is no scheme name and quarter" in {
    val request = FakeRequest(GET, routes.CannotChangeAFTReturnController.onPageLoad(SampleData.srn, SampleData.startDate, SampleData.accessType, SampleData.versionInt).url)

    mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))
    val result = route(application, request).value

    status(result) mustEqual SEE_OTHER
    redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
  }
}
