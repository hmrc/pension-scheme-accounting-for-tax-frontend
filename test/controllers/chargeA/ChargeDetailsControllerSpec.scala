/*
 * Copyright 2021 HM Revenue & Customs
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

package controllers.chargeA

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.chargeA.ChargeDetailsFormProvider
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.{NormalMode, GenericViewModel, UserAnswers}
import models.chargeA.ChargeDetails
import models.requests.IdentifierRequest
import org.mockito.{Matchers, ArgumentCaptor}
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, when, verify}
import pages.chargeA.ChargeDetailsPage
import play.api.Application
import play.api.data.Form
import play.api.libs.json.{Json, JsObject}
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

class ChargeDetailsControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers {
  private val userAnswers: Option[UserAnswers] = Some(userAnswersWithSchemeNamePstrQuarter)
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build()
  private val templateToBeRendered = "chargeA/chargeDetails.njk"
  private val form = new ChargeDetailsFormProvider().apply(minimumChargeValueAllowed = BigDecimal("0.01"))

  private def httpPathGET: String = controllers.chargeA.routes.ChargeDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt).url

  private def httpPathPOST: String = controllers.chargeA.routes.ChargeDetailsController.onSubmit(NormalMode, srn, startDate, accessType, versionInt).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "numberOfMembers" -> Seq("44"),
    "totalAmtOfTaxDueAtLowerRate" -> Seq("33.44"),
    "totalAmtOfTaxDueAtHigherRate" -> Seq("34.34")
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "numberOfMembers" -> Seq("999999999999999999999999999999999999999"),
    "totalAmtOfTaxDueAtLowerRate" -> Seq("33.44"),
    "totalAmtOfTaxDueAtHigherRate" -> Seq("34.34")
  )

  private val valuesWithZeroAmount: Map[String, Seq[String]] = Map(
    "numberOfMembers" -> Seq("44"),
    "totalAmtOfTaxDueAtLowerRate" -> Seq("0.00"),
    "totalAmtOfTaxDueAtHigherRate" -> Seq("0.00")
  )

  private val jsonToPassToTemplate: Form[ChargeDetails] => JsObject = form => Json.obj(
    "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.chargeA.routes.ChargeDetailsController.onSubmit(NormalMode, srn, startDate, accessType, versionInt).url,
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
      schemeName = schemeName)
  )

  override def beforeEach: Unit = {
    super.beforeEach
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockCompoundNavigator.nextPage(Matchers.eq(ChargeDetailsPage), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)
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
        "chargeADetails" -> Json.obj(ChargeDetailsPage.toString -> Json.toJson(chargeAChargeDetails))
      )

      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).save(any(), jsonCaptor.capture)(any(), any())

      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(dummyCall.url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
    }

    "return a BAD REQUEST when zero amounts are submitted where in precompile mode" in {

      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      mutableFakeDataRetrievalAction.setSessionData(sessionData(sessionAccessData = sessionAccessDataPreCompile))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesWithZeroAmount)).value

      status(result) mustEqual BAD_REQUEST
    }

    "return redirect when zero amounts are submitted where new return flag is NOT set" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      mutableFakeDataRetrievalAction.setSessionData(sessionData(sessionAccessData = sessionAccessDataCompile))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesWithZeroAmount)).value

      status(result) mustEqual SEE_OTHER
    }

    "redirect to Session Expired page for a POST when there is no data" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
    }
  }
}
