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
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData._
import forms.ChargeTypeFormProvider
import matchers.JsonMatchers
import models.ChargeType.ChargeTypeAnnualAllowance
import models.{ChargeType, Enumerable, GenericViewModel, UserAnswers}
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, Matchers}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import pages.{ChargeTypePage, IsPsaSuspendedQuery}
import play.api.Application
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import services.{AFTService, AllowAccessService}
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

class ChargeTypeControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  import ChargeTypeControllerSpec._

  private val mockAuditService = mock[AuditService]
  private val mockAllowAccessService = mock[AllowAccessService]
  private val mockAFTService = mock[AFTService]
  private val retrievedUA = userAnswersWithSchemeName
    .setOrException(IsPsaSuspendedQuery, value = false)
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction

  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[AuditService].toInstance(mockAuditService),
    bind[AllowAccessService].toInstance(mockAllowAccessService),
    bind[AFTService].toInstance(mockAFTService)
  )

  val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  private val jsonToTemplate: Form[ChargeType] => JsObject = form => Json.obj(
    fields = "form" -> form,
    "radios" -> ChargeType.radios(form),
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.routes.ChargeTypeController.onSubmit(srn, startDate).url,
      returnUrl = dummyCall.url,
      schemeName = SampleData.schemeName)
  )

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockAllowAccessService, mockUserAnswersCacheConnector, mockRenderer, mockAFTService, mockAppConfig)
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAllowAccessService.filterForIllegalPageAccess(any(), any())(any())).thenReturn(Future.successful(None))
    when(mockAFTService.retrieveAFTRequiredDetails(any(), any(), any())(any(), any(), any())).thenReturn(Future.successful((schemeDetails, retrievedUA)))
    when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(dummyCall.url)

  }

  "ChargeType Controller" when {
    "on a GET" must {

      "return OK with the correct view and call the aft service" in {
        mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))
        val templateCaptor = ArgumentCaptor.forClass(classOf[String])
        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

        val result = route(application, httpGETRequest(httpPathGETVersion)).value

        status(result) mustEqual OK

        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
        verify(mockAFTService, times(1)).retrieveAFTRequiredDetails(Matchers.eq(srn), Matchers.eq(startDate), Matchers.eq(None))(any(), any(), any())
        verify(mockAllowAccessService, times(1)).filterForIllegalPageAccess(Matchers.eq(srn), Matchers.eq(retrievedUA))(any())

        templateCaptor.getValue mustEqual template
        jsonCaptor.getValue must containJson(jsonToTemplate.apply(form))
      }

      "return alternative location when allow access service returns alternative location" in {
        val location = "redirect"
        val alternativeLocation = Redirect(location)
        mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))
        when(mockAllowAccessService.filterForIllegalPageAccess(any(), any())(any())).thenReturn(Future.successful(Some(alternativeLocation)))

        whenReady(route(application, httpGETRequest(httpPathGETVersion)).value) { result =>
          result.header.status mustEqual SEE_OTHER
          result.header.headers.get(LOCATION) mustBe Some(location)
        }
      }

      "return OK and the correct view for a GET when the question has previously been answered" in {
        val ua = SampleData.userAnswersWithSchemeName.set(ChargeTypePage, ChargeTypeAnnualAllowance).get

        mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))
        val templateCaptor = ArgumentCaptor.forClass(classOf[String])
        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

        when(mockAFTService.retrieveAFTRequiredDetails(any(), any(), any())(any(), any(), any())).thenReturn(Future.successful((schemeDetails, ua)))

        val result = route(application, httpGETRequest(httpPathGETVersion)).value

        status(result) mustEqual OK

        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

        templateCaptor.getValue mustEqual template
        jsonCaptor.getValue must containJson(jsonToTemplate.apply(form.fill(ChargeTypeAnnualAllowance)))
      }

      "send the AFTStart Audit Event" in {
        reset(mockAuditService)
        val eventCaptor = ArgumentCaptor.forClass(classOf[StartAFTAuditEvent])
        mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))

        val result = route(application, httpGETRequest(httpPathGETVersion)).value

        status(result) mustEqual OK

        verify(mockAuditService, times(1)).sendEvent(eventCaptor.capture())(any(), any())
        eventCaptor.getValue mustEqual StartAFTAuditEvent(SampleData.psaId, SampleData.pstr)
      }

      "redirect to aft summary page when the user is locked and coming to charge type page" in {
        mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))
        mutableFakeDataRetrievalAction.setViewOnly(true)

        val result = route(application, httpGETRequest(httpPathGETVersion)).value

        redirectLocation(result).value mustBe controllers.routes.AFTSummaryController.onPageLoad(srn, None).url

        verify(mockRenderer, never()).render(any(), any())(any())
        verify(mockAFTService, never()).retrieveAFTRequiredDetails(Matchers.eq(srn), Matchers.eq(startDate), Matchers.eq(None))(any(), any(), any())
        verify(mockAllowAccessService, never()).filterForIllegalPageAccess(Matchers.eq(srn), Matchers.eq(retrievedUA))(any())
      }
    }

    "on a POST" must {
      "Save data to user answers and redirect to next page when valid data is submitted" in {

        val expectedJson = Json.obj(ChargeTypePage.toString -> Json.toJson(ChargeTypeAnnualAllowance)(writes(ChargeType.enumerable)))

        when(mockCompoundNavigator.nextPage(Matchers.eq(ChargeTypePage), any(), any(), any())).thenReturn(SampleData.dummyCall)

        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

        val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

        status(result) mustEqual SEE_OTHER

        verify(mockUserAnswersCacheConnector, times(1)).save(any(), jsonCaptor.capture)(any(), any())

        jsonCaptor.getValue must containJson(expectedJson)

        redirectLocation(result) mustBe Some(SampleData.dummyCall.url)

      }

      "return a BAD REQUEST when invalid data is submitted" in {
        val application = applicationBuilder(userAnswers = userAnswers).build()

        val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

        status(result) mustEqual BAD_REQUEST

        verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())

      }

      "redirect to Session Expired page for a POST when there is no data" in {
        val application = applicationBuilder(userAnswers = None).build()

        val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
      }
    }
  }
}

object ChargeTypeControllerSpec {
  private val template = "chargeType.njk"

  private def form = new ChargeTypeFormProvider()()

  private def httpPathGETVersion: String = controllers.routes.ChargeTypeController.onPageLoad(srn, startDate).url

  private def httpPathPOST: String = controllers.routes.ChargeTypeController.onSubmit(srn, startDate).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "value" -> Seq(ChargeTypeAnnualAllowance.toString)
  )
  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "value" -> Seq("Unknown Charge")
  )
  private val userAnswers: Option[UserAnswers] = Some(SampleData.userAnswersWithSchemeName)
}
