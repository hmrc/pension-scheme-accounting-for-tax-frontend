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

package controllers.chargeC

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.chargeC.ChargeDetailsFormProvider
import matchers.JsonMatchers
import models.chargeC.ChargeCDetails
import models.GenericViewModel
import models.NormalMode
import models.UserAnswers
import org.mockito.Matchers.any
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.mockito.ArgumentCaptor
import org.mockito.Matchers
import pages.chargeC.ChargeCDetailsPage
import pages.chargeC.SponsoringOrganisationDetailsPage
import pages.chargeC.WhichTypeOfSponsoringEmployerPage
import play.api.Application
import play.api.data.Form
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future
import models.LocalDateBinder._
import utils.AFTConstants.QUARTER_END_DATE
import utils.AFTConstants.QUARTER_START_DATE

class ChargeDetailsControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers {
  private val userAnswers: Option[UserAnswers] = Some(userAnswersWithSchemeNameAndOrganisation)
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build()
  private val templateToBeRendered = "chargeC/chargeDetails.njk"
  private val form = new ChargeDetailsFormProvider().apply(QUARTER_START_DATE, QUARTER_END_DATE, minimumChargeValueAllowed = BigDecimal("0.01"))
  private val index = 0
  private def httpPathGET: String = controllers.chargeC.routes.ChargeDetailsController.onPageLoad(NormalMode, srn, startDate, index).url
  private def httpPathPOST: String = controllers.chargeC.routes.ChargeDetailsController.onSubmit(NormalMode, srn, startDate, index).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "paymentDate.day" -> Seq(QUARTER_START_DATE.getDayOfMonth.toString),
    "paymentDate.month" -> Seq(QUARTER_START_DATE.getMonthValue.toString),
    "paymentDate.year" -> Seq(QUARTER_START_DATE.getYear.toString),
    "amountTaxDue" -> Seq("33.44")
  )

  private val valuesWithZeroAmount: Map[String, Seq[String]] = Map(
    "paymentDate.day" -> Seq(QUARTER_START_DATE.getDayOfMonth.toString),
    "paymentDate.month" -> Seq(QUARTER_START_DATE.getMonthValue.toString),
    "paymentDate.year" -> Seq(QUARTER_START_DATE.getYear.toString),
    "amountTaxDue" -> Seq("0.00")
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "paymentDate.day" -> Seq.empty,
    "paymentDate.month" -> Seq("4"),
    "paymentDate.year" -> Seq("2019"),
    "amountTaxDue" -> Seq("33.44")
  )

  private val jsonToPassToTemplate:Form[ChargeCDetails]=>JsObject = form => Json.obj(
    "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.chargeC.routes.ChargeDetailsController.onSubmit(NormalMode, srn, startDate, index).url,
      returnUrl = dummyCall.url,
      schemeName = schemeName)
  )

  override def beforeEach: Unit = {
    super.beforeEach
    when(mockUserAnswersCacheConnector.save(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(dummyCall.url)
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

    "return OK and the correct view for a GET when the question has previously been answered" in {
      val ua = userAnswers.map(_.set(ChargeCDetailsPage(index), chargeCDetails)).get.toOption.get
      
      mutableFakeDataRetrievalAction.setDataToReturn(Option(ua))

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(form.fill(chargeCDetails)))
    }

    "redirect to Session Expired page for a GET when there is no data" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
    }

    "Save data to user answers and redirect to next page when valid data is submitted" in {

      val expectedJson = Json.obj(
        "chargeCDetails" -> Json.obj(
          "employers" -> Json.arr(Json.obj(
          SponsoringOrganisationDetailsPage.toString -> sponsoringOrganisationDetails,
          WhichTypeOfSponsoringEmployerPage.toString -> "organisation",
          ChargeCDetailsPage.toString -> Json.toJson(chargeCDetails)
            )
          )
        )
      )

      when(mockCompoundNavigator.nextPage(Matchers.eq(ChargeCDetailsPage(index)), any(), any(), any(), any())).thenReturn(dummyCall)

      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).save(any(), jsonCaptor.capture, any(), any())(any(), any())

      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(dummyCall.url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any(), any(), any())(any(), any())
    }

    "return a BAD REQUEST when zero amount is submitted and in precompile mode" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      mutableFakeDataRetrievalAction.setSessionData(sessionData(sessionAccessData = sessionAccessDataPreCompile))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesWithZeroAmount)).value

      status(result) mustEqual BAD_REQUEST
    }

    "Return a redirect when zero amount is submitted and new return flag is NOT set" in {
      when(mockCompoundNavigator.nextPage(Matchers.eq(ChargeCDetailsPage(index)), any(), any(), any(), any())).thenReturn(dummyCall)

      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

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