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

package controllers.paymentsAndCharges

import java.time.LocalDate

import config.FrontendAppConfig
import connectors.FinancialStatementConnector
import connectors.cache.FinancialInfoCacheConnector
import controllers.actions.{FakeIdentifierAction, IdentifierAction}
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.financialStatement.SchemeFS
import models.financialStatement.SchemeFSChargeType.PSS_AFT_RETURN
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.mockito.{ArgumentCaptor, Matchers}
import org.scalatest.BeforeAndAfterEach
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{route, _}
import play.twirl.api.Html
import services.SchemeService
import services.paymentsAndCharges.PaymentsAndChargesService
import uk.gov.hmrc.nunjucks.NunjucksRenderer
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

class PaymentsAndChargesControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with BeforeAndAfterEach {

  import PaymentsAndChargesControllerSpec._

  private def httpPathGET(year: Int = year): String =
    controllers.paymentsAndCharges.routes.PaymentsAndChargesController.onPageLoad(srn, year).url

  private val mockSchemeService: SchemeService = mock[SchemeService]
  private val mockFinancialStatementConnector: FinancialStatementConnector = mock[FinancialStatementConnector]
  private val mockFICacheConnector: FinancialInfoCacheConnector = mock[FinancialInfoCacheConnector]
  private val mockPaymentsAndChargesService: PaymentsAndChargesService = mock[PaymentsAndChargesService]
  private val application: Application = new GuiceApplicationBuilder()
    .overrides(
      Seq[GuiceableModule](
        bind[IdentifierAction].to[FakeIdentifierAction],
        bind[NunjucksRenderer].toInstance(mockRenderer),
        bind[FrontendAppConfig].toInstance(mockAppConfig),
        bind[SchemeService].toInstance(mockSchemeService),
        bind[FinancialStatementConnector].toInstance(mockFinancialStatementConnector),
        bind[FinancialInfoCacheConnector].toInstance(mockFICacheConnector),
        bind[PaymentsAndChargesService].toInstance(mockPaymentsAndChargesService)
      ): _*
    )
    .build()

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockSchemeService, mockFinancialStatementConnector, mockRenderer, mockPaymentsAndChargesService, mockFICacheConnector)
    when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(dummyCall.url)
    when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any())).thenReturn(Future.successful(schemeDetails))
    when(mockFinancialStatementConnector.getSchemeFS(any())(any(), any())).thenReturn(Future.successful(schemeFSResponse))
    when(mockPaymentsAndChargesService.getPaymentsAndCharges(Matchers.eq(filteredSchemeFS), Matchers.eq(srn), any())(any(), any(), any()))
      .thenReturn(Nil)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockFICacheConnector.save(any())(any(), any())).thenReturn(Future.successful(Json.obj()))
  }

  private def expectedJson: JsObject = Json.obj(
    fields = "seqPaymentsAndChargesTable" -> Nil,
    "schemeName" -> schemeDetails.schemeName,
    "returnUrl" -> dummyCall.url
  )

  "PaymentsAndChargesController" must {

    "return OK and the correct view with filtered payments and charges information for a GET" in {
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      val result = route(application, httpGETRequest(httpPathGET())).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual "paymentsAndCharges/paymentsAndCharges.njk"
      jsonCaptor.getValue must containJson(expectedJson)
    }

    "redirect to Session Expired page when there is no data for the selected year for a GET" in {
      val result = route(application, httpGETRequest(httpPathGET(year = 2022))).value
      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
    }
  }
}

object PaymentsAndChargesControllerSpec {
  private val year = 2020
  private val srn = "test-srn"
  private def createCharge(startDate: String, endDate: String, chargeReference: String): SchemeFS = {
    SchemeFS(
      chargeReference = chargeReference,
      chargeType = PSS_AFT_RETURN,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 56432.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = 1029.05,
      accruedInterestTotal = 0.00,
      periodStartDate = LocalDate.parse(startDate),
      periodEndDate = LocalDate.parse(endDate)
    )
  }
  private val schemeFSResponse: Seq[SchemeFS] = Seq(
    createCharge(startDate = "2020-04-01", endDate = "2020-06-30", chargeReference = "XY002610150184"),
    createCharge(startDate = "2020-01-01", endDate = "2020-03-31", chargeReference = "AYU3494534632"),
    createCharge(startDate = "2021-04-01", endDate = "2021-06-30", chargeReference = "XY002610150185")
  )

  private val filteredSchemeFS: Seq[(LocalDate, Seq[SchemeFS])] = Seq(
    (
      LocalDate.parse("2020-01-01"),
      Seq(
        createCharge(startDate = "2020-01-01", endDate = "2020-03-31", chargeReference = "AYU3494534632")
      )
    ),
    (
      LocalDate.parse("2020-04-01"),
      Seq(
        createCharge(startDate = "2020-04-01", endDate = "2020-06-30", chargeReference = "XY002610150184")
      )
    )
  )
}
