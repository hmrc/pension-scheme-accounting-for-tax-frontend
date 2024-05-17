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

package controllers

import controllers.base.ControllerSpecBase
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import services.SchemeService
import services.financialOverview.scheme.PaymentsAndChargesService

import scala.concurrent.{ExecutionContext, Future}


class AFTOverviewControllerSpec extends ControllerSpecBase {
  private val mockPaymentsAndChargesService: PaymentsAndChargesService = mock[PaymentsAndChargesService]
  private val mockSchemeService: SchemeService = mock[SchemeService]
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  "AFT Overview Controller" must {

    "must return OK and the correct view for a GET" in {

      when(mockRenderer.render(any(), any())(any()))
        .thenReturn(Future.successful(Html("foo")))

      val application = applicationBuilder(userAnswers = None).build()

      val srn: String = "S2012345678"

      val request = FakeRequest(GET, routes.AFTOverviewController.onPageLoad(srn).url)

      val result = route(application, request).value

      status(result) mustEqual OK

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), any())(any())

      templateCaptor.getValue mustEqual "aftOverview.njk"

      application.stop()
    }

    //"AFT Overview Controller" must {

  "return InternalServerError when paymentsAndChargesService fails" in {
    when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any()))
      .thenReturn(Future.failed(new RuntimeException("Test exception")))

    val application = applicationBuilder(userAnswers = None).build()

    val srn: String = "S2012345678"

    val request = FakeRequest(GET, routes.AFTOverviewController.onPageLoad(srn).url)

    val result = route(application, request).value

    status(result) mustEqual INTERNAL_SERVER_ERROR

    application.stop()
  }

  "return InternalServerError when schemeService fails to retrieve scheme details" in {
    when(mockSchemeService.retrieveSchemeDetails(any(), any(), any()))
      .thenReturn(Future.failed(new RuntimeException("Test exception")))

    val application = applicationBuilder(userAnswers = None).build()

    val srn: String = "S2012345678"

    val request = FakeRequest(GET, routes.AFTOverviewController.onPageLoad(srn).url)

    val result = route(application, request).value

    status(result) mustEqual INTERNAL_SERVER_ERROR

    application.stop()
  }
}
}
