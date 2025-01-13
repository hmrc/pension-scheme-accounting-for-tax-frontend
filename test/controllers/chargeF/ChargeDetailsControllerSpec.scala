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
import data.SampleData._
import forms.chargeF.ChargeDetailsFormProvider
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.requests.IdentifierRequest
import models.{NormalMode, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import pages.chargeF.ChargeDetailsPage
import play.api.Application
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers._
import utils.AFTConstants._
import views.html.chargeF.ChargeDetailsView

import scala.concurrent.Future

class ChargeDetailsControllerSpec extends ControllerSpecBase with JsonMatchers {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build()
  private val startDate = QUARTER_START_DATE
  private val endDate = QUARTER_END_DATE
  private val form = new ChargeDetailsFormProvider()(startDate, endDate, BigDecimal("0.01"))

  private def httpPathGET: String = controllers.chargeF.routes.ChargeDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt).url

  private def httpPathPOST: String = controllers.chargeF.routes.ChargeDetailsController.onSubmit(NormalMode, srn, startDate, accessType, versionInt).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "deregistrationDate.day" -> Seq("3"),
    "deregistrationDate.month" -> Seq("4"),
    "deregistrationDate.year" -> Seq("2020"),
    "amountTaxDue" -> Seq("33.44")
  )

  private val valuesWithZeroAmount: Map[String, Seq[String]] = Map(
    "deregistrationDate.day" -> Seq("3"),
    "deregistrationDate.month" -> Seq("4"),
    "deregistrationDate.year" -> Seq("2020"),
    "amountTaxDue" -> Seq("0.00")
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "deregistrationDate.day" -> Seq("32"),
    "deregistrationDate.month" -> Seq("13"),
    "deregistrationDate.year" -> Seq("2003"),
    "amountTaxDue" -> Seq("33.44")
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
  }

  private val userAnswers: Option[UserAnswers] = Some(userAnswersWithSchemeNamePstrQuarter)

  "ChargeDetails Controller" must {
    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      val request = httpGETRequest(httpPathGET)

      val view = application.injector.instanceOf[ChargeDetailsView].apply(
        form,
        schemeName,
        controllers.chargeF.routes.ChargeDetailsController.onSubmit(NormalMode, srn, startDate, accessType, versionInt),
        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url
      )(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
     }

    "return OK and the correct view for a GET when the question has previously been answered" in {
      val ua = userAnswers.map(_.set(ChargeDetailsPage, chargeFChargeDetails)).get.toOption.get

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))
      val request = httpGETRequest(httpPathGET)

      val view = application.injector.instanceOf[ChargeDetailsView].apply(
        form.fill(chargeFChargeDetails),
        schemeName,
        controllers.chargeF.routes.ChargeDetailsController.onSubmit(NormalMode, srn, startDate, accessType, versionInt),
        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url
      )(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)

    }

    "redirect to Session Expired page for a GET when there is no data" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }

    "Save data to user answers and redirect to next page when valid data is submitted" in {

      val expectedJson = Json.obj(
        "chargeFDetails" -> Json.obj(ChargeDetailsPage.toString -> Json.toJson(chargeFChargeDetails))
      )

      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(ChargeDetailsPage), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)

      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), jsonCaptor.capture, any(), any())(any(), any())

      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(dummyCall.url)
    }

    "return a BAD REQUEST when zero value is submitted and in precompile mode" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      mutableFakeDataRetrievalAction.setSessionData(sessionData(sessionAccessData = sessionAccessDataPreCompile))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesWithZeroAmount)).value

      status(result) mustEqual BAD_REQUEST
    }

    "return a redirect when zero value is submitted and new return flag is NOT set" in {

      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(ChargeDetailsPage), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)

      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      mutableFakeDataRetrievalAction.setSessionData(sessionData(sessionAccessData = sessionAccessDataCompile))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesWithZeroAmount)).value

      status(result) mustEqual SEE_OTHER
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).savePartial(any(), any(), any(), any())(any(), any())
    }

    "redirect to Session Expired page for a POST when there is no data" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }
}
