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

package controllers.chargeB

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.chargeB.ChargeDetailsFormProvider
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.chargeB.ChargeBDetails
import models.{GenericViewModel, NormalMode, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import pages.chargeB.ChargeBDetailsPage
import play.api.Application
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.AFTConstants.QUARTER_START_DATE

import scala.concurrent.Future

class ChargeDetailsControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers {
  private val userAnswers: Option[UserAnswers] = Some(userAnswersWithSchemeNamePstrQuarter)
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build()
  private val templateToBeRendered = "chargeB/chargeDetails.njk"
  private val form = new ChargeDetailsFormProvider().apply(minimumChargeValueAllowed = BigDecimal("0.01"))

  private def httpPathGET: String = controllers.chargeB.routes.ChargeDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt).url

  private def httpPathPOST: String = controllers.chargeB.routes.ChargeDetailsController.onSubmit(NormalMode, srn, startDate, accessType, versionInt).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "numberOfDeceased" -> Seq("4"),
    "amountTaxDue" -> Seq("33.44")
  )

  private val valuesWithZeroAmount: Map[String, Seq[String]] = Map(
    "numberOfDeceased" -> Seq("4"),
    "amountTaxDue" -> Seq("0.00")
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "numberOfDeceased" -> Seq("1.5"),
    "amountTaxDue" -> Seq("33.44")
  )

  private val jsonToPassToTemplate: Form[ChargeBDetails] => JsObject = form => Json.obj(
    "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.chargeB.routes.ChargeDetailsController.onSubmit(NormalMode, srn, startDate, accessType, versionInt).url,
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, QUARTER_START_DATE, accessType, versionInt).url,
      schemeName = schemeName)
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
  }

  "ChargeDetails Controller" must {

    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate.apply(form))
    }

    "Save data to user answers and redirect to next page when valid data is submitted" in {

      val expectedJson = Json.obj(
        "chargeBDetails" -> Json.obj(ChargeBDetailsPage.toString -> Json.toJson(chargeBDetails))
      )

      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(ChargeBDetailsPage), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)

      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), jsonCaptor.capture, any(), any())(any(), any())

      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(dummyCall.url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).savePartial(any(), any(), any(), any())(any(), any())
    }

    "return a BAD REQUEST when zero amount is submitted and in precompile mode" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      mutableFakeDataRetrievalAction.setSessionData(sessionData(sessionAccessData = sessionAccessDataPreCompile))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesWithZeroAmount)).value

      status(result) mustEqual BAD_REQUEST
    }

    "return a redirect when zero amount is submitted and new return flag is NOT set" in {
      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(ChargeBDetailsPage), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)

      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
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
