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
import data.SampleData._
import forms.chargeD.ChargeDetailsFormProvider
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.requests.IdentifierRequest
import models.{NormalMode, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import pages.chargeD.{ChargeDetailsPage, MemberDetailsPage}
import play.api.Application
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{redirectLocation, route, status, _}
import utils.AFTConstants.{QUARTER_END_DATE, QUARTER_START_DATE}
import views.html.chargeD.ChargeDetailsView

import scala.concurrent.Future

class ChargeDetailsControllerSpec extends ControllerSpecBase with JsonMatchers {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private def application: Application = registerApp(applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build())
  private val form = new ChargeDetailsFormProvider().apply(QUARTER_START_DATE, QUARTER_END_DATE, minimumChargeValueAllowed = BigDecimal("0.01"))

  private def httpPathGET: String = controllers.chargeD.routes.ChargeDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt, 0).url

  private def httpPathPOST: String = controllers.chargeD.routes.ChargeDetailsController.onSubmit(NormalMode, srn, startDate, accessType, versionInt, 0).url

  private val valuesValid: Map[String, Seq[String]] = Map(

    "dateOfEvent.day" -> Seq(QUARTER_START_DATE.getDayOfMonth.toString),
    "dateOfEvent.month" -> Seq(QUARTER_START_DATE.getMonthValue.toString),
    "dateOfEvent.year" -> Seq(QUARTER_START_DATE.getYear.toString),
    "taxAt25Percent" -> Seq("33.44"),
    "taxAt55Percent" -> Seq("50.00")
  )

  private val valuesWithZeroAmount: Map[String, Seq[String]] = Map(
    "dateOfEvent.day" -> Seq(QUARTER_START_DATE.getDayOfMonth.toString),
    "dateOfEvent.month" -> Seq(QUARTER_START_DATE.getMonthValue.toString),
    "dateOfEvent.year" -> Seq(QUARTER_START_DATE.getYear.toString),
    "taxAt25Percent" -> Seq("0.00"),
    "taxAt55Percent" -> Seq("0.00")
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "dateOfEvent.day" -> Seq("32"),
    "dateOfEvent.month" -> Seq("13"),
    "dateOfEvent.year" -> Seq("2003"),
    "taxAt25Percent" -> Seq("33.44"),
    "taxAt55Percent" -> Seq("33.44")
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[?])).thenReturn(dummyCall.url)
  }

  val validData: UserAnswers = userAnswersWithSchemeNamePstrQuarter.set(MemberDetailsPage(0), memberDetails).get
  val expectedJson: JsObject = validData.set(ChargeDetailsPage(0), chargeDDetails).get.data
  val index =0

  "ChargeDetails Controller" must {
    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(validData))
      val request = httpGETRequest(httpPathGET)

      val view = application.injector.instanceOf[ChargeDetailsView].apply(
        form,
        schemeName,
        controllers.chargeD.routes.ChargeDetailsController.onSubmit(NormalMode, srn, startDate, accessType, versionInt, index),
        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
        memberName = "first last"
      )(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return OK and the correct view for a GET when the question has previously been answered" in {
      val ua = validData.set(ChargeDetailsPage(0), chargeDDetails).get

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))
      val request = httpGETRequest(httpPathGET)

      val view = application.injector.instanceOf[ChargeDetailsView].apply(
        form.fill(chargeDDetails),
        schemeName,
        controllers.chargeD.routes.ChargeDetailsController.onSubmit(NormalMode, srn, startDate, accessType, versionInt, index),
        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
        memberName = "first last"
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

      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(ChargeDetailsPage(0)), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(validData))

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), jsonCaptor.capture, any(), any())(any(), any())
      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(dummyCall.url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(validData))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).savePartial(any(), any(), any(), any())(any(), any())
    }

    "return a BAD REQUEST when zero amount is submitted and in precompile mode" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(validData))
      mutableFakeDataRetrievalAction.setSessionData(sessionData(sessionAccessData = sessionAccessDataPreCompile))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesWithZeroAmount)).value

      status(result) mustEqual BAD_REQUEST
    }

    "return a redirect when zero amount is submitted and new return flag is NOT set" in {

      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(ChargeDetailsPage(0)), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(validData))
      mutableFakeDataRetrievalAction.setSessionData(sessionData(sessionAccessData = sessionAccessDataCompile))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesWithZeroAmount)).value

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
