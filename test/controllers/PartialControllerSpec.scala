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

import connectors.FinancialStatementConnector
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData._
import matchers.JsonMatchers
import models.Enumerable
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{Json, JsObject}
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import services.{AFTPartialService, SchemeService}
import services.AFTPartialServiceSpec.allTypesMultipleReturnsModel
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

class PartialControllerSpec
    extends ControllerSpecBase
    with NunjucksSupport
    with JsonMatchers
    with BeforeAndAfterEach
    with Enumerable.Implicits
    with Results
    with ScalaFutures {

  private def httpPathGET: String = controllers.routes.PartialController.aftPartial(srn).url
  private def httpPathPaymentsAndCharges: String = controllers.routes.PartialController.paymentsAndChargesPartial(srn).url
  private val mockAftPartialService: AFTPartialService = mock[AFTPartialService]
  private val mockSchemeService: SchemeService = mock[SchemeService]
  private val mockFinancialStatementConnector: FinancialStatementConnector = mock[FinancialStatementConnector]
  private val extraModules: Seq[GuiceableModule] =
    Seq[GuiceableModule](
      bind[AFTPartialService].toInstance(mockAftPartialService),
      bind[SchemeService].toInstance(mockSchemeService),
      bind[FinancialStatementConnector].toInstance(mockFinancialStatementConnector)
    )
  val application: Application = applicationBuilder(extraModules = extraModules).build()

  private val templateToBeRendered = "partials/overview.njk"
  private val templateToBeRenderedForPaymentsAndCharges = "partials/paymentsAndCharges.njk"
  private val jsonToPassToTemplate: JsObject = Json.obj("aftModels" -> Json.toJson(allTypesMultipleReturnsModel))
  private val jsonToPassToTemplatePaymentsAndCharges: JsObject =
    Json.obj("redirectUrl" -> dummyCall.url)
  private val templateCaptor = ArgumentCaptor.forClass(classOf[String])
  private val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockAftPartialService, mockRenderer)
    when(mockAftPartialService.retrieveOptionAFTViewModel(any(), any())(any(), any()))
      .thenReturn(Future.successful(allTypesMultipleReturnsModel))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.paymentsAndChargesUrl).thenReturn(dummyCall.url)
    when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any())).thenReturn(Future.successful(schemeDetails))
    when(mockFinancialStatementConnector.getSchemeFS(any())(any(), any())).thenReturn(Future.successful(schemeFSResponseAftAndOTC))
  }

  "Partial Controller" when {
    "on a GET" must {

      "return the html with information received from overview api" in {
        when(mockAftPartialService.retrieveOptionAFTViewModel(any(), any())(any(), any()))
          .thenReturn(Future.successful(allTypesMultipleReturnsModel))
        val result = route(application, httpGETRequest(httpPathGET)).value

        status(result) mustEqual OK

        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

        templateCaptor.getValue mustEqual templateToBeRendered

        jsonCaptor.getValue must containJson(jsonToPassToTemplate)
      }
    }

    "paymentsAndChargesPartial" must {

      "return the html with the information from payments and charges partial" in {
        val result = route(application, httpGETRequest(httpPathPaymentsAndCharges)).value

        status(result) mustEqual OK

        val xx = Await.result(result, Duration.Inf)

        //println( "\n>>>" + xx.body.as("String"))

        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

        templateCaptor.getValue mustEqual templateToBeRenderedForPaymentsAndCharges

        jsonCaptor.getValue must containJson(jsonToPassToTemplatePaymentsAndCharges)
      }

      "return the empty html when there no information from payments and charges partial" in {
        when(mockFinancialStatementConnector.getSchemeFS(any())(any(), any())).thenReturn(Future.successful(Seq.empty))
        val result = route(application, httpGETRequest(httpPathPaymentsAndCharges)).value

        status(result) mustEqual OK
        contentAsString(result) mustEqual ""
        verify(mockRenderer, times(0)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
      }
    }

  }
}
