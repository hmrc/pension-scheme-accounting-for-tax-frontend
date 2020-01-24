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

import audit.{AuditService, StartAFTAuditEvent}
import controllers.base.ControllerSpecBase
import data.SampleData
import forms.ChargeTypeFormProvider
import matchers.JsonMatchers
import models.ChargeType.ChargeTypeAnnualAllowance
import models.{ChargeType, Enumerable, GenericViewModel, NormalMode, UserAnswers}
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, Matchers, Mockito}
import org.scalatest.BeforeAndAfterEach
import pages.ChargeTypePage
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, route, status, _}
import play.twirl.api.Html
import services.SchemeService
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

class ChargeTypeControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with BeforeAndAfterEach with Enumerable.Implicits {

  import ChargeTypeControllerSpec._

  private val mockSchemeService = mock[SchemeService]
  private val mockAuditService = mock[AuditService]

  private val jsonToTemplate: Form[ChargeType] => JsObject = form => Json.obj(
    fields = "form" -> form,
    "radios" -> ChargeType.radios(form),
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.routes.ChargeTypeController.onSubmit(NormalMode, SampleData.srn).url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(SampleData.srn),
      schemeName = SampleData.schemeName)
  )

  override def beforeEach: Unit = {
    super.beforeEach
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
  }

  "ChargeType Controller" when {
    "on a GET" must {

      "return OK with the correct view and save the quarter, aft status, scheme name, pstr" in {
        val application = new GuiceApplicationBuilder()
          .overrides(
            modules(Some(SampleData.userAnswersWithSchemeName)) ++ Seq[GuiceableModule](
              bind[SchemeService].toInstance(mockSchemeService)
            ): _*
          ).build()

        when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any())).thenReturn(Future.successful(SampleData.schemeDetails))

        val templateCaptor = ArgumentCaptor.forClass(classOf[String])
        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

        val result = route(application, FakeRequest(GET, httpPathGET)).value

        status(result) mustEqual OK

        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
        verify(mockUserAnswersCacheConnector, times(1)).save(any(), any())(any(), any())

        templateCaptor.getValue mustEqual template

        jsonCaptor.getValue must containJson(jsonToTemplate.apply(form))

        application.stop()
      }

      "return OK and the correct view for a GET when the question has previously been answered" in {
        val ua = SampleData.userAnswersWithSchemeName.set(ChargeTypePage, ChargeTypeAnnualAllowance).get
        val application = new GuiceApplicationBuilder()
          .overrides(
            modules(Some(ua)) ++ Seq[GuiceableModule](
              bind[SchemeService].toInstance(mockSchemeService)
            ): _*
          ).build()

        when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any())).thenReturn(Future.successful(SampleData.schemeDetails))

        val templateCaptor = ArgumentCaptor.forClass(classOf[String])
        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

        val result = route(application, FakeRequest(GET, httpPathGET)).value

        status(result) mustEqual OK

        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

        templateCaptor.getValue mustEqual template

        jsonCaptor.getValue must containJson(jsonToTemplate(form.fill(ChargeTypeAnnualAllowance)))

        application.stop()
      }

      "send the AFTStart Audit Event" in {
        Mockito.reset(mockAuditService)
        val ua = SampleData.userAnswersWithSchemeName.set(ChargeTypePage, ChargeTypeAnnualAllowance).get
        val application = new GuiceApplicationBuilder()
          .overrides(
            modules(Some(ua)) ++ Seq[GuiceableModule](
              bind[SchemeService].toInstance(mockSchemeService),
              bind[AuditService].toInstance(mockAuditService)
            ): _*
          ).build()
        val eventCaptor = ArgumentCaptor.forClass(classOf[StartAFTAuditEvent])
        when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any())).thenReturn(Future.successful(SampleData.schemeDetails))

        val result = route(application, FakeRequest(GET, httpPathGET)).value

        status(result) mustEqual OK
        verify(mockAuditService, times(1)).sendEvent(eventCaptor.capture())(any(), any())
        eventCaptor.getValue mustEqual StartAFTAuditEvent(SampleData.psaId, SampleData.pstr)
        application.stop()
      }
    }

    "on a POST" must {
      "Save data to user answers and redirect to next page when valid data is submitted" in {

        val expectedJson = Json.obj(ChargeTypePage.toString -> Json.toJson(ChargeTypeAnnualAllowance)(writes(ChargeType.enumerable)))

        when(mockCompoundNavigator.nextPage(Matchers.eq(ChargeTypePage), any(), any(), any())).thenReturn(SampleData.dummyCall)

        val application = applicationBuilder(userAnswers = userAnswers).build()

        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

        val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

        status(result) mustEqual SEE_OTHER

        verify(mockUserAnswersCacheConnector, times(1)).save(any(), jsonCaptor.capture)(any(), any())

        jsonCaptor.getValue must containJson(expectedJson)

        redirectLocation(result) mustBe Some(SampleData.dummyCall.url)

        application.stop()
      }

      "return a BAD REQUEST when invalid data is submitted" in {
        val application = applicationBuilder(userAnswers = userAnswers).build()

        val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

        status(result) mustEqual BAD_REQUEST

        verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())

        application.stop()
      }

      "redirect to Session Expired page for a POST when there is no data" in {
        val application = applicationBuilder(userAnswers = None).build()

        val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
        application.stop()
      }
    }
  }
}

object ChargeTypeControllerSpec {
  private val template = "chargeType.njk"

  private def form = new ChargeTypeFormProvider()()

  private def httpPathGET: String = controllers.routes.ChargeTypeController.onPageLoad(NormalMode, SampleData.srn).url

  private def httpPathPOST: String = controllers.routes.ChargeTypeController.onSubmit(NormalMode, SampleData.srn).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "value" -> Seq(ChargeTypeAnnualAllowance.toString)
  )
  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "value" -> Seq("Unknown Charge")
  )
  private val userAnswers: Option[UserAnswers] = Some(SampleData.userAnswersWithSchemeName)
}
