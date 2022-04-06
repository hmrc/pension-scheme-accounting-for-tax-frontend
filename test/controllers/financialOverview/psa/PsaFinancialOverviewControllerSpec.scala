/*
 * Copyright 2022 HM Revenue & Customs
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

package controllers.financialOverview.psa

import connectors.{FinancialStatementConnector, MinimalConnector}
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.Enumerable
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import services.AFTPartialService
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

class PsaFinancialOverviewControllerSpec
  extends ControllerSpecBase
    with NunjucksSupport
    with JsonMatchers
    with BeforeAndAfterEach
    with Enumerable.Implicits
    with Results
    with ScalaFutures {

  private def getPartial: String = routes.PsaFinancialOverviewController.psaFinancialOverview.url

  private val mockAFTPartialService: AFTPartialService = mock[AFTPartialService]
  private val mockFinancialStatementConnector: FinancialStatementConnector = mock[FinancialStatementConnector]
  private val mockMinimalPsaConnector: MinimalConnector = mock[MinimalConnector]
  private val extraModules: Seq[GuiceableModule] =
    Seq[GuiceableModule](
      bind[AFTPartialService].toInstance(mockAFTPartialService),
      bind[FinancialStatementConnector].toInstance(mockFinancialStatementConnector),
      bind[MinimalConnector].toInstance(mockMinimalPsaConnector)
    )
  val application: Application = applicationBuilder(extraModules = extraModules).build()

  private val templateCaptor = ArgumentCaptor.forClass(classOf[String])
  private val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
  private val psaName="John Doe"
  val requestRefundUrl = s"test.com?requestType=3&psaName=$psaName&availAmt=1000"
  private val jsonToPassToTemplate:JsObject =  Json.obj(
    "totalUpcomingCharge" -> "10",
      "totalOverdueCharge" -> "10",
      "totalInterestAccruing" -> "10",
      "psaName" -> "John Doe",
      "requestRefundUrl" ->  routes.PsaRequestRefundController.onPageLoad.url,
      "creditBalanceFormatted" -> "£1,000.00",
      "creditBalance" -> 1000
  )
  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockAFTPartialService, mockRenderer,mockAppConfig)
    when(mockAppConfig.creditBalanceRefundLink).thenReturn("test.com")
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockFinancialStatementConnector.getPsaFSWithPaymentOnAccount(any())(any(), any()))
      .thenReturn(Future.successful(psaFs))

  }

  "PsaFinancialOverviewController" when {
    "schemeFinancialOverview" must {

      "return the html with information received from overview api" in {
        when(mockAFTPartialService.retrievePsaChargesAmount(any()))
          .thenReturn(("10", "10", "10"))

        when(mockFinancialStatementConnector.getPsaFSWithPaymentOnAccount(any())(any(), any()))
          .thenReturn(Future.successful(psaFs))
        when(mockAFTPartialService.getCreditBalanceAmount(any()))
          .thenReturn(BigDecimal("1000"))
        when(mockMinimalPsaConnector.getPsaOrPspName(any(),any(), any()))
          .thenReturn(Future.successful(psaName))

        val result = route(application, httpGETRequest(getPartial)).value

        status(result) mustEqual OK
        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
        templateCaptor.getValue mustEqual "financialOverview/psaFinancialOverview.njk"
        jsonCaptor.getValue must containJson(jsonToPassToTemplate)
      }
    }

  }

}
