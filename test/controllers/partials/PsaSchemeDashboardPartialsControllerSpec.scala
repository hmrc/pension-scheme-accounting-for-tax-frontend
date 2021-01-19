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

package controllers.partials

import connectors.FinancialStatementConnector
import controllers.base.ControllerSpecBase
import data.SampleData.{srn, _}
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
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import services.{PsaSchemePartialService, SchemeService}
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

class PsaSchemeDashboardPartialsControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers
    with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  import services.PsaSchemePartialServiceSpec._
  private def getPartial: String = routes.PsaSchemeDashboardPartialsController.psaSchemeDashboardPartial(srn).url

  private val mockPsaSchemePartialService: PsaSchemePartialService = mock[PsaSchemePartialService]
  private val mockSchemeService: SchemeService = mock[SchemeService]
  private val mockFinancialStatementConnector: FinancialStatementConnector = mock[FinancialStatementConnector]
  private val extraModules: Seq[GuiceableModule] =
    Seq[GuiceableModule](
      bind[PsaSchemePartialService].toInstance(mockPsaSchemePartialService),
      bind[SchemeService].toInstance(mockSchemeService),
      bind[FinancialStatementConnector].toInstance(mockFinancialStatementConnector)
    )
  val application: Application = applicationBuilder(extraModules = extraModules).build()

  private val cardsPartialJson: JsObject =
    Json.obj("cards" -> Json.toJson(allTypesMultipleReturnsModel))


  private val templateCaptor = ArgumentCaptor.forClass(classOf[String])
  private val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockPsaSchemePartialService, mockRenderer)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(schemeDetails))
    when(mockFinancialStatementConnector.getSchemeFS(any())(any(), any()))
      .thenReturn(Future.successful(schemeFSResponseAftAndOTC))
  }

  "PsaSchemeDashboardPartials Controller" when {
    "aftPartial" must {

      "return the html with information received from overview api" in {
        when(mockPsaSchemePartialService.aftCardModel(any(), any())(any(), any()))
          .thenReturn(Future.successful(allTypesMultipleReturnsModel))
        when(mockPsaSchemePartialService.upcomingAftChargesModel(any(), any())(any()))
          .thenReturn(allTypesMultipleReturnsModel)
        when(mockPsaSchemePartialService.overdueAftChargesModel(any(), any())(any()))
          .thenReturn(allTypesMultipleReturnsModel)

        val result = route(application, httpGETRequest(getPartial)).value

        status(result) mustEqual OK

        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

        templateCaptor.getValue mustEqual "partials/psaSchemeDashboardPartial.njk"
      }
    }

  }
}
