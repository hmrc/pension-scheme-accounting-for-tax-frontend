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

import behaviours.ControllerBehaviours
import connectors.SchemeDetailsConnector
import data.SampleData
import forms.YearRangeFormProvider
import models.YearRange.CurrentYear
import models.{Enumerable, GenericViewModel, NormalMode, YearRange}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import pages.chargeE.{AnnualAllowanceMembersQuery, AnnualAllowanceYearPage}
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, route, status, _}

import scala.concurrent.Future

class AnnualAllowanceYearControllerSpec extends ControllerBehaviours with BeforeAndAfterEach with Enumerable.Implicits {

  private val template = "chargeE/annualAllowanceYear.njk"
  private val mockSchemeDetailsConnector = mock[SchemeDetailsConnector]
  private val valuesValid: Map[String, Seq[String]] = Map(
    "value" -> Seq(CurrentYear.toString)
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
      val ua = SampleData.userAnswersWithSchemeName.set(AnnualAllowanceYearPage(0), CurrentYear).get
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

      jsonCaptor.getValue must containJson(jsonToTemplate(form.fill(CurrentYear)))

      application.stop()
    }

    val expectedJson = Json.obj(
      "chargeEDetails" -> Json.obj(
        AnnualAllowanceMembersQuery.toString -> Json.arr(
          Json.obj(
            AnnualAllowanceYearPage.toString -> Json.toJson(CurrentYear.toString)
          )
        )
      )
    )

    behave like controllerWithPOSTWithJson(
      httpPath = httpPathPOST,
      page = AnnualAllowanceYearPage(0),
      expectedJson = expectedJson,
      form = form,
      templateToBeRendered = template,
      requestValuesValid = valuesValid,
      requestValuesInvalid = valuesInvalid
    )
  }
}
