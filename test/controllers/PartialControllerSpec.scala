/*
 * Copyright 2021 HM Revenue & Customs
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
import services.AFTPartialServiceSpec._
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

class PartialControllerSpec
  extends ControllerSpecBase
    with NunjucksSupport
    with JsonMatchers
    with BeforeAndAfterEach
    with Enumerable.Implicits
    with Results
    with ScalaFutures {

  private def aftPartial: String = controllers.routes.PartialController.aftPartial(srn).url

  private def paymentsAndChargesPartial: String = controllers.routes.PartialController.paymentsAndChargesPartial(srn).url

  private def pspDashboardAftReturnsPartial: String = controllers.routes.PartialController.pspDashboardAftReturnsPartial().url

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

  private val aftPartialJson: JsObject =
    Json.obj("aftModels" -> Json.toJson(allTypesMultipleReturnsModel))
  private val paymentsAndChargesPartialJson: JsObject =
    Json.obj("redirectUrl" -> dummyCall.url)
  private val pspDashboardAftReturnsPartialJson: JsObject =
    Json.obj("aft" -> Json.toJson(pspDashboardAftReturnsViewModel))
  private val templateCaptor = ArgumentCaptor.forClass(classOf[String])
  private val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockAftPartialService, mockRenderer)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.paymentsAndChargesUrl).thenReturn(dummyCall.url)
    when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(schemeDetails))
    when(mockFinancialStatementConnector.getSchemeFS(any())(any(), any()))
      .thenReturn(Future.successful(schemeFSResponseAftAndOTC))
  }

  "Partial Controller" when {
    "aftPartial" must {

      "return the html with information received from overview api" in {
        when(
          mockAftPartialService.retrieveOptionAFTViewModel(
            srn = any(),
            psaId = any(),
            schemeIdType = any()
          )(any(), any())
        ).thenReturn(Future.successful(allTypesMultipleReturnsModel))

        val result = route(application, httpGETRequest(aftPartial)).value

        status(result) mustEqual OK

        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

        templateCaptor.getValue mustEqual "partials/overview.njk"

        jsonCaptor.getValue must containJson(aftPartialJson)
      }
    }

    "paymentsAndChargesPartial" must {

      "return the html with the information from payments and charges partial" in {
        val result = route(application, httpGETRequest(paymentsAndChargesPartial)).value

        status(result) mustEqual OK

        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

        templateCaptor.getValue mustEqual "partials/paymentsAndCharges.njk"

        jsonCaptor.getValue must containJson(paymentsAndChargesPartialJson)
      }

      "not render the fin info section when there are no payments or charges" in {
        when(mockFinancialStatementConnector.getSchemeFS(any())(any(), any())).thenReturn(Future.successful(Seq.empty))
        val result = route(application, httpGETRequest(paymentsAndChargesPartial)).value

        status(result) mustEqual OK
        verify(mockRenderer, times(0)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
      }
    }

    "pspDashboardAftReturnsPartial" must {
      "return the html with the information for AFT returns" in {

        when(
          mockAftPartialService.retrievePspDashboardAftReturnsModel(
            srn = any(),
            pspId = any(),
            schemeIdType = any(),
            authorisingPsaId = any()
          )(any(), any())
        ).thenReturn(Future.successful(pspDashboardAftReturnsViewModel))

        val result = route(
          app = application,
          req = httpGETRequest(pspDashboardAftReturnsPartial)
            .withHeaders(
              "idNumber" -> SampleData.srn,
              "schemeIdType" -> "srn",
              "psaId" -> SampleData.pspId,
              "authorisingPsaId" -> SampleData.psaId
            )
        ).value

        status(result) mustEqual OK

        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

        templateCaptor.getValue mustEqual "partials/pspDashboardAftReturnsCard.njk"

        jsonCaptor.getValue must containJson(pspDashboardAftReturnsPartialJson)
      }
    }
  }
}
