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

import connectors.AFTConnector
import controllers.base.ControllerSpecBase
import data.SampleData
import forms.AFTSummaryFormProvider
import matchers.JsonMatchers
import models.{Enumerable, GenericViewModel, NormalMode, UserAnswers}
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, Matchers, Mockito}
import org.scalatest.BeforeAndAfterEach
import pages.{AFTSummaryPage, PSTRQuery, SchemeNameQuery}
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import services.SchemeService
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}
import utils.AFTSummaryHelper

import scala.concurrent.Future

class AFTSummaryControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with BeforeAndAfterEach with Enumerable.Implicits {

  private val mockSchemeService = mock[SchemeService]

  private val mockAftConnector: AFTConnector = mock[AFTConnector]

  override protected def applicationBuilder(userAnswers: Option[UserAnswers] = None): GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .overrides(
        modules(userAnswers) ++ Seq[GuiceableModule](
          bind[SchemeService].toInstance(mockSchemeService),
          bind[AFTConnector].toInstance(mockAftConnector)
        ): _*
      )

  private val templateToBeRendered = "aftSummary.njk"
  private val form = new AFTSummaryFormProvider()()

  private def httpPathGETNoVersion: String = controllers.routes.AFTSummaryController.onPageLoad(NormalMode, SampleData.srn, None).url

  private def httpPathGETVersion: String = controllers.routes.AFTSummaryController.onPageLoad(NormalMode, SampleData.srn, Some(SampleData.version)).url

  private def httpPathPOST: String = controllers.routes.AFTSummaryController.onSubmit(NormalMode, SampleData.srn, None).url

  private val valuesValid: Map[String, Seq[String]] = Map("value" -> Seq("true"))

  private val valuesInvalid: Map[String, Seq[String]] = Map("value" -> Seq("xyz"))

  private val summaryHelper = new AFTSummaryHelper

  private val schemeName = "scheme"
  private val schemePSTR = "pstr"

  private val uaGetAFTDetails = UserAnswers()
  private val uaGetAFTDetailsPlusSchemeDetails = uaGetAFTDetails
    .set(SchemeNameQuery, schemeName).toOption.getOrElse(uaGetAFTDetails)
    .set(PSTRQuery, schemePSTR).toOption.getOrElse(uaGetAFTDetails)


  override def beforeEach: Unit = {
    super.beforeEach()
    Mockito.reset(mockSchemeService, mockAftConnector, mockUserAnswersCacheConnector, mockRenderer)
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any())).thenReturn(Future.successful(SampleData.schemeDetails))
    when(mockAftConnector.getAFTDetails(any(), any(), any())(any(), any())).thenReturn(Future.successful(uaGetAFTDetails.data))

  }


  private def jsonToPassToTemplate(version: Option[String]): Form[Boolean] => JsObject = form => Json.obj(
    "form" -> form,
    "list" -> summaryHelper.summaryListData(UserAnswers(), SampleData.srn),
    "viewModel" -> GenericViewModel(
      submitUrl = routes.AFTSummaryController.onSubmit(NormalMode, SampleData.srn, version).url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(SampleData.srn),
      schemeName = SampleData.schemeName),
    "radios" -> Radios.yesNo(form("value"))
  )

  private val userAnswers: Option[UserAnswers] = Some(SampleData.userAnswersWithSchemeName)

  "AFTSummary Controller" must {
    "return OK and the correct view for a GET where no version is present in the request" in {
      val application = applicationBuilder(userAnswers = Some(SampleData.userAnswersWithSchemeName)).build()
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGETNoVersion)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(version = None).apply(form))

      application.stop()
    }

    "return OK and the correct view for a GET where a version is present in the request" in {
      val application = applicationBuilder(userAnswers = Some(SampleData.userAnswersWithSchemeName)).build()

      val pstrCaptor = ArgumentCaptor.forClass(classOf[String])
      val startDateCaptor = ArgumentCaptor.forClass(classOf[String])
      val versionCaptor = ArgumentCaptor.forClass(classOf[String])

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGETVersion)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(version = Some(SampleData.version)).apply(form))

      verify(mockAftConnector, times(1)).getAFTDetails(pstrCaptor.capture(), startDateCaptor.capture, versionCaptor.capture)(any(), any())

      pstrCaptor.getValue mustEqual SampleData.pstr
      startDateCaptor.getValue mustEqual "2020-04-01"
      versionCaptor.getValue mustEqual SampleData.version

      application.stop()
    }

    "Save data to user answers and redirect to next page when valid data is submitted" in {

      when(mockCompoundNavigator.nextPage(Matchers.eq(AFTSummaryPage), any(), any(), any())).thenReturn(SampleData.dummyCall)

      val application = applicationBuilder(userAnswers = userAnswers).build()

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).save(any(), jsonCaptor.capture)(any(), any())

      jsonCaptor.getValue must containJson(Json.obj(AFTSummaryPage.toString -> Json.toJson(true)))

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
