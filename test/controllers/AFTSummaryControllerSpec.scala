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

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData._
import forms.AFTSummaryFormProvider
import matchers.JsonMatchers
import models.{Enumerable, GenericViewModel, Quarter, UserAnswers}
import org.mockito.Matchers.any
import org.mockito.Mockito.{never, reset, times, verify, when}
import org.mockito.{ArgumentCaptor, Matchers}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import pages._
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Call, Results}
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import services.{AFTService, AllowAccessService}
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}
import utils.AFTSummaryHelper

import scala.concurrent.Future
import models.LocalDateBinder._

class AFTSummaryControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  private val mockAllowAccessService = mock[AllowAccessService]
  private val mockAFTService = mock[AFTService]

  private val extraModules: Seq[GuiceableModule] =
    Seq[GuiceableModule](
      bind[AllowAccessService].toInstance(mockAllowAccessService),
      bind[AFTService].toInstance(mockAFTService)
    )

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()

  private val application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  private val templateToBeRendered = "aftSummary.njk"
  private val form = new AFTSummaryFormProvider()()

  private def httpPathGETNoVersion: String = controllers.routes.AFTSummaryController.onPageLoad(SampleData.srn, startDate, None).url

  private def httpPathGETVersion: String = controllers.routes.AFTSummaryController.onPageLoad(SampleData.srn, startDate, Some(SampleData.version)).url

  private def httpPathPOST: String = controllers.routes.AFTSummaryController.onSubmit(SampleData.srn, startDate, None).url

  private val valuesValid: Map[String, Seq[String]] = Map("value" -> Seq("true"))

  private val valuesInvalid: Map[String, Seq[String]] = Map("value" -> Seq("xyz"))

  private val summaryHelper = new AFTSummaryHelper

  private val retrievedUA = userAnswersWithSchemeNamePstrQuarter
    .setOrException(IsPsaSuspendedQuery, value = false)

  private val testManagePensionsUrl = Call("GET", "/scheme-summary")

  private val uaGetAFTDetails = UserAnswers().set(QuarterPage, Quarter("2000-04-01", "2000-05-31")).toOption.get

  override def beforeEach: Unit = {
    super.beforeEach()
    reset(mockAllowAccessService, mockUserAnswersCacheConnector, mockRenderer, mockAFTService, mockAppConfig)
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(uaGetAFTDetails.data))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAllowAccessService.filterForIllegalPageAccess(any(), any())(any())).thenReturn(Future.successful(None))
    when(mockAFTService.retrieveAFTRequiredDetails(any(), any(), any())(any(), any(), any())).thenReturn(Future.successful((schemeDetails, retrievedUA)))
    when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(testManagePensionsUrl.url)
  }


  private def jsonToPassToTemplate(version: Option[String]): Form[Boolean] => JsObject = form => Json.obj(
    "form" -> form,
    "list" -> summaryHelper.summaryListData(UserAnswers(), SampleData.srn, startDate),
    "viewModel" -> GenericViewModel(
      submitUrl = routes.AFTSummaryController.onSubmit(SampleData.srn, startDate, version).url,
      returnUrl = testManagePensionsUrl.url,
      schemeName = SampleData.schemeName),
    "radios" -> Radios.yesNo(form("value"))
  )

  private val userAnswers: Option[UserAnswers] = Some(SampleData.userAnswersWithSchemeNamePstrQuarter)

  "AFTSummary Controller" must {
    "return OK and the correct view for a GET where no version is present in the request and call the aft service" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGETNoVersion)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
      verify(mockAFTService, times(1)).retrieveAFTRequiredDetails(Matchers.eq(srn), Matchers.eq(startDate), Matchers.eq(None))(any(), any(), any())
      verify(mockAllowAccessService, times(1)).filterForIllegalPageAccess(Matchers.eq(srn), Matchers.eq(retrievedUA))(any())

      templateCaptor.getValue mustEqual templateToBeRendered
      jsonCaptor.getValue must containJson(jsonToPassToTemplate(version = None).apply(form))
    }

    "return alternative location when allow access service returns alternative location" in {
      val location = "redirect"
      val alternativeLocation = Redirect(location)
      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter))
      when(mockAllowAccessService.filterForIllegalPageAccess(any(), any())(any())).thenReturn(Future.successful(Some(alternativeLocation)))

      whenReady(route(application, httpGETRequest(httpPathGETNoVersion)).value) { result =>
        result.header.status mustEqual SEE_OTHER
        result.header.headers.get(LOCATION) mustBe Some(location)
      }
    }

    "return OK and the correct view for a GET where a version is present in the request" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGETVersion)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
      verify(mockAFTService, times(1)).retrieveAFTRequiredDetails(Matchers.eq(srn), Matchers.eq(startDate), Matchers.eq(Some(version)))(any(), any(), any())
      verify(mockAllowAccessService, times(1)).filterForIllegalPageAccess(Matchers.eq(srn), Matchers.eq(retrievedUA))(any())

      templateCaptor.getValue mustEqual templateToBeRendered
      jsonCaptor.getValue must containJson(jsonToPassToTemplate(version = Some(version)).apply(form))
    }

    "redirect to next page when user selects yes" in {

      when(mockCompoundNavigator.nextPage(Matchers.eq(AFTSummaryPage), any(), any(), any(), any())).thenReturn(SampleData.dummyCall)

      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, never()).removeAll(any())(any(), any())

      redirectLocation(result) mustBe Some(SampleData.dummyCall.url)
    }

    "remove all data and redirect to scheme summary page when user selects no" in {
      when(mockUserAnswersCacheConnector.removeAll(any())(any(), any())).thenReturn(Future.successful(Ok))

      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      val result = route(application, httpPOSTRequest(httpPathPOST, Map("value" -> Seq("false")))).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result) mustBe Some(testManagePensionsUrl.url)
      verify(mockUserAnswersCacheConnector, times(1)).removeAll(any())(any(), any())
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
    }

    "redirect to Session Expired page for a POST when there is no data" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
      application.stop()
    }
  }
}
