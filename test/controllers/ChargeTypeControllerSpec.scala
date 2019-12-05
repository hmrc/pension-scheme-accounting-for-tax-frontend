/*
 * Copyright 2019 HM Revenue & Customs
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

import behaviours.ControllerBehaviours
import connectors.SchemeDetailsConnector
import data.SampleData
import forms.ChargeTypeFormProvider
import models.ChargeType.ChargeTypeAnnualAllowance
import models.{ChargeType, Enumerable, GenericViewModel, NormalMode}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import pages.ChargeTypePage
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, route, status, _}

import scala.concurrent.Future

class ChargeTypeControllerSpec extends ControllerBehaviours with BeforeAndAfterEach with Enumerable.Implicits {

  private val template = "chargeType.njk"

  private def form = new ChargeTypeFormProvider()()

  private def chargeTypeGetRoute: String = controllers.routes.ChargeTypeController.onPageLoad(NormalMode, SampleData.srn).url

  private def chargeTypePostRoute: String = controllers.routes.ChargeTypeController.onSubmit(NormalMode, SampleData.srn).url

  private val mockSchemeDetailsConnector = mock[SchemeDetailsConnector]

  private val valuesValid: Map[String, Seq[String]] = Map(
    "value" -> Seq(ChargeTypeAnnualAllowance.toString)
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "value" -> Seq("Unknown Charge")
  )

  private val jsonToTemplate: Form[ChargeType] => JsObject = form => Json.obj(
    fields = "form" -> form,
    "radios" -> ChargeType.radios(form),
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.routes.ChargeTypeController.onSubmit(NormalMode, SampleData.srn).url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(SampleData.srn),
      schemeName = SampleData.schemeName)
  )

  "ChargeDetails Controller" must {

    "return OK and the correct view for a GET" in {
      val application = new GuiceApplicationBuilder()
        .overrides(
          modules(Some(SampleData.userAnswersWithSchemeName)) ++ Seq[GuiceableModule](
            bind[SchemeDetailsConnector].toInstance(mockSchemeDetailsConnector)
          ): _*
        ).build()
      when(mockSchemeDetailsConnector.getSchemeName(any(), any(), any())(any(), any())).thenReturn(Future.successful(SampleData.schemeName))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, FakeRequest(GET, chargeTypeGetRoute)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual template

      jsonCaptor.getValue must containJson(jsonToTemplate.apply(form))

      application.stop()
    }

    "return OK and the correct view for a GET when the question has previously been answered" in {
      reset(mockSchemeDetailsConnector)
      val ua = SampleData.userAnswersWithSchemeName.set(ChargeTypePage, ChargeTypeAnnualAllowance).get
      val application = new GuiceApplicationBuilder()
        .overrides(
          modules(Some(ua)) ++ Seq[GuiceableModule](
            bind[SchemeDetailsConnector].toInstance(mockSchemeDetailsConnector)
          ): _*
        ).build()
      when(mockSchemeDetailsConnector.getSchemeName(any(), any(), any())(any(), any())).thenReturn(Future.successful(SampleData.schemeName))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, FakeRequest(GET, chargeTypeGetRoute)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual template

      jsonCaptor.getValue must containJson(jsonToTemplate(form.fill(ChargeTypeAnnualAllowance)))

      application.stop()
    }

    behave like controllerWithPOST(
      httpPath = chargeTypePostRoute,
      page = ChargeTypePage,
      data = ChargeTypeAnnualAllowance,
      form = form,
      templateToBeRendered = template,
      requestValuesValid = valuesValid,
      requestValuesInvalid = valuesInvalid
    )
  }
}
