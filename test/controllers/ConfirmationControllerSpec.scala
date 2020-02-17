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

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData.{dummyCall, userAnswersWithSchemeName}
import matchers.JsonMatchers
import models.GenericViewModel
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Call
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import utils.AFTConstants._

import scala.concurrent.Future

class ConfirmationControllerSpec extends ControllerSpecBase with JsonMatchers {
  private val testManagePensionsUrl = Call("GET", "/scheme-summary")
  private val quarterEndDate = LocalDate.parse(QUARTER_END_DATE).format(DateTimeFormatter.ofPattern("d MMMM yyyy"))
  private val quarterStartDate = LocalDate.parse(QUARTER_START_DATE).format(DateTimeFormatter.ofPattern("d MMMM"))

  private def submitUrl = Call("GET", s"/manage-pension-scheme-accounting-for-tax/sign-out/${SampleData.srn}")

  private val json = Json.obj(
    fields = "srn" -> SampleData.srn,
    "pstr" -> SampleData.pstr,
    "pensionSchemesUrl" -> testManagePensionsUrl.url,
    "quarterStartDate" -> quarterStartDate,
    "quarterEndDate" -> quarterEndDate,
    "submittedDate" -> DateTimeFormatter.ofPattern("d MMMM yyyy 'at' hh:mm a").format(LocalDateTime.now()),
    "viewModel" -> GenericViewModel(
      submitUrl = submitUrl.url,
      returnUrl = dummyCall.url,
      schemeName = SampleData.schemeName)
  )

  "Confirmation Controller" must {

    "return OK and the correct view for a GET" in {
      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(dummyCall.url)
      when(mockAppConfig.yourPensionSchemesUrl).thenReturn(testManagePensionsUrl.url)
      when(mockUserAnswersCacheConnector.removeAll(any())(any(), any())).thenReturn(Future.successful(Ok))

      val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
      val application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build()

      val request = FakeRequest(GET, routes.ConfirmationController.onPageLoad(SampleData.srn).url)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))

      val result = route(application, request).value

      status(result) mustEqual OK

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
      verify(mockUserAnswersCacheConnector, times(1)).removeAll(any())(any(), any())

      templateCaptor.getValue mustEqual "confirmation.njk"
      jsonCaptor.getValue must containJson(json)
    }
  }
}
