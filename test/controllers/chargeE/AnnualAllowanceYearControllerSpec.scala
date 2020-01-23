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

package controllers.chargeE

import connectors.SchemeDetailsConnector
import controllers.base.ControllerSpecBase
import data.SampleData
import forms.YearRangeFormProvider
import matchers.JsonMatchers
import models.{Enumerable, GenericViewModel, NormalMode, UserAnswers, YearRange}
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.mockito.{ArgumentCaptor, Matchers}
import org.scalatest.BeforeAndAfterEach
import pages.chargeE.{AnnualAllowanceMembersQuery, AnnualAllowanceYearPage}
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, route, status, _}
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

class AnnualAllowanceYearControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with BeforeAndAfterEach with Enumerable.Implicits {

  private val template = "chargeE/annualAllowanceYear.njk"
  private val mockSchemeDetailsConnector = mock[SchemeDetailsConnector]
  private val valuesValid: Map[String, Seq[String]] = Map(
    "value" -> Seq(YearRange.currentYear.toString)
  )
  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "value" -> Seq("Unknown Year")
  )
  private val jsonToTemplate: Form[YearRange] => JsObject = form => Json.obj(
    fields = "form" -> form,
    "radios" -> YearRange.radios(form),
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.chargeE.routes.AnnualAllowanceYearController.onSubmit(NormalMode, SampleData.srn, 0).url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(SampleData.srn),
      schemeName = SampleData.schemeName)
  )

  private def form = new YearRangeFormProvider()()

  private def httpPathGET: String = controllers.chargeE.routes.AnnualAllowanceYearController.onPageLoad(NormalMode, SampleData.srn, 0).url

  private def httpPathPOST: String = controllers.chargeE.routes.AnnualAllowanceYearController.onSubmit(NormalMode, SampleData.srn, 0).url

  override def beforeEach: Unit = {
    super.beforeEach
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
  }

  private val userAnswers: Option[UserAnswers] = Some(SampleData.userAnswersWithSchemeName)

  "AnnualAllowanceYear Controller" must {

    "return OK and the correct view for a GET" in {
      val application = new GuiceApplicationBuilder()
        .overrides(
          modules(Some(SampleData.userAnswersWithSchemeName)) ++ Seq[GuiceableModule](
            bind[SchemeDetailsConnector].toInstance(mockSchemeDetailsConnector)
          ): _*
        ).build()
      when(mockSchemeDetailsConnector.getSchemeDetails(any(), any(), any())(any(), any())).thenReturn(Future.successful(SampleData.schemeDetails))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, FakeRequest(GET, httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual template

      jsonCaptor.getValue must containJson(jsonToTemplate.apply(form))

      application.stop()
    }

    "return OK and the correct view for a GET when the question has previously been answered" in {
      reset(mockSchemeDetailsConnector)
      val ua = SampleData.userAnswersWithSchemeName.set(AnnualAllowanceYearPage(0), YearRange.currentYear).get
      val application = new GuiceApplicationBuilder()
        .overrides(
          modules(Some(ua)) ++ Seq[GuiceableModule](
            bind[SchemeDetailsConnector].toInstance(mockSchemeDetailsConnector)
          ): _*
        ).build()
      when(mockSchemeDetailsConnector.getSchemeDetails(any(), any(), any())(any(), any())).thenReturn(Future.successful(SampleData.schemeDetails))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, FakeRequest(GET, httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual template

      jsonCaptor.getValue must containJson(jsonToTemplate(form.fill(YearRange.currentYear)))

      application.stop()
    }


    "Save data to user answers and redirect to next page when valid data is submitted" in {

      val expectedJson = Json.obj(
        "chargeEDetails" -> Json.obj(
          AnnualAllowanceMembersQuery.toString -> Json.arr(
            Json.obj(
              AnnualAllowanceYearPage.toString -> Json.toJson(YearRange.currentYear.toString)
            )
          )
        )
      )

      when(mockCompoundNavigator.nextPage(Matchers.eq(AnnualAllowanceYearPage(0)), any(), any(), any())).thenReturn(SampleData.dummyCall)

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
