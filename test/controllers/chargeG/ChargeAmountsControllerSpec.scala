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
import forms.chargeG.ChargeAmountsFormProvider
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.requests.IdentifierRequest
import models.{NormalMode, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.ArgumentMatchers
import pages.chargeG.{ChargeAmountsPage, MemberDetailsPage}
import play.api.Application
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, route, status, _}
import views.html.chargeG.ChargeAmountsView

import scala.concurrent.Future

class ChargeAmountsControllerSpec extends ControllerSpecBase with JsonMatchers {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build()
  private val form = new ChargeAmountsFormProvider()("first last", BigDecimal("0.01"))

  private def httpPathGET: String = controllers.chargeG.routes.ChargeAmountsController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt, 0).url

  private def httpPathPOST: String = controllers.chargeG.routes.ChargeAmountsController.onSubmit(NormalMode, srn, startDate, accessType, versionInt, 0).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "amountTransferred" -> Seq("33.44"),
    "amountTaxDue" -> Seq("50.00")
  )

  private val valuesZero: Map[String, Seq[String]] = Map(
    "amountTransferred" -> Seq("0.00"),
    "amountTaxDue" -> Seq("0.00")
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "amountTransferred" -> Seq("33.44"),
    "amountTaxDue" -> Seq.empty
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
  }

  val validData: UserAnswers = userAnswersWithSchemeNamePstrQuarter.set(MemberDetailsPage(0), memberGDetails).get
  val expectedJson: JsObject = validData.set(ChargeAmountsPage(0), chargeAmounts).get.data

  val request: FakeRequest[_] = httpGETRequest(httpPathGET)

  "ChargeAmounts Controller" must {
    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(validData))
      val result = route(application, httpGETRequest(httpPathGET)).value
      val view = application.injector.instanceOf[ChargeAmountsView].apply(
        form,
        "first last",
        controllers.chargeG.routes.ChargeAmountsController.onSubmit(NormalMode, srn, startDate, accessType, versionInt, 0),
        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
        schemeName
      )(request, messages)

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return OK and the correct view for a GET when the question has previously been answered" in {
      val ua = validData.set(ChargeAmountsPage(0), chargeAmounts).get

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val view = application.injector.instanceOf[ChargeAmountsView].apply(
        form.fill(chargeAmounts),
        "first last",
        controllers.chargeG.routes.ChargeAmountsController.onSubmit(NormalMode, srn, startDate, accessType, versionInt, 0),
        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
        schemeName
      )(request, messages)

      val result = route(application, httpGETRequest(httpPathGET)).value

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

      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(ChargeAmountsPage(0)), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(validData))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result) mustBe Some(dummyCall.url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(validData))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
    }


    "return a BAD REQUEST when zero amount is submitted and in precompile mode" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(validData))
      mutableFakeDataRetrievalAction.setSessionData(sessionData(sessionAccessData = sessionAccessDataPreCompile))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesZero)).value

      status(result) mustEqual BAD_REQUEST
    }

    "return a redirect when zero amount is submitted and the new return flag is NOT set" in {
      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(ChargeAmountsPage(0)), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(validData))
      mutableFakeDataRetrievalAction.setSessionData(sessionData(sessionAccessData = sessionAccessDataCompile))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesZero)).value

      status(result) mustEqual SEE_OTHER
    }

    "redirect to Session Expired page for a POST when there is no data" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }
}
