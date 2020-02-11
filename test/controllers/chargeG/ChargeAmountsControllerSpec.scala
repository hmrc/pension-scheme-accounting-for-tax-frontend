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

package controllers.chargeG

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.chargeG.ChargeAmountsFormProvider
import matchers.JsonMatchers
import models.chargeG.ChargeAmounts
import models.{GenericViewModel, NormalMode, UserAnswers}
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, Matchers}
import pages.IsNewReturn
import pages.chargeG.{ChargeAmountsPage, MemberDetailsPage}
import play.api.Application
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{redirectLocation, route, status, _}
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

class ChargeAmountsControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build()
  private val templateToBeRendered = "chargeG/chargeAmounts.njk"
  private val form = new ChargeAmountsFormProvider()("first last", BigDecimal("0.01"))
  private def httpPathGET: String = controllers.chargeG.routes.ChargeAmountsController.onPageLoad(NormalMode, srn, 0).url
  private def httpPathPOST: String = controllers.chargeG.routes.ChargeAmountsController.onSubmit(NormalMode, srn, 0).url

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

  private val jsonToPassToTemplate:Form[ChargeAmounts]=>JsObject = form => Json.obj(
    "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.chargeG.routes.ChargeAmountsController.onSubmit(NormalMode, srn, 0).url,
      returnUrl = dummyCall.url,
      schemeName = schemeName),
    "memberName" -> "first last"
  )

  override def beforeEach: Unit = {
    super.beforeEach
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(dummyCall.url)
  }

  val validData: UserAnswers = userAnswersWithSchemeName.set(MemberDetailsPage(0), memberGDetails).get
  val expectedJson: JsObject = validData.set(ChargeAmountsPage(0), chargeAmounts).get.data

  "ChargeAmounts Controller" must {
    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(validData))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate.apply(form))
    }

    "return OK and the correct view for a GET when the question has previously been answered" in {
      val ua = validData.set(ChargeAmountsPage(0), chargeAmounts).get

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(form.fill(chargeAmounts)))
    }

    "redirect to Session Expired page for a GET when there is no data" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
    }

    "Save data to user answers and redirect to next page when valid data is submitted" in {

      when(mockCompoundNavigator.nextPage(Matchers.eq(ChargeAmountsPage(0)), any(), any(), any())).thenReturn(dummyCall)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(validData))

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).save(any(), jsonCaptor.capture)(any(), any())
      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(dummyCall.url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(validData))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
    }


    "return a BAD REQUEST when zero amount is submitted and the new return flag is set" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(validData.setOrException(IsNewReturn, true)))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesZero)).value

      status(result) mustEqual BAD_REQUEST
    }

    "return a redirect when zero amount is submitted and the new return flag is NOT set" in {
      when(mockCompoundNavigator.nextPage(Matchers.eq(ChargeAmountsPage(0)), any(), any(), any())).thenReturn(dummyCall)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(validData))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesZero)).value

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
