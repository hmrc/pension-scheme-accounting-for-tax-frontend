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

package controllers.financialStatement.paymentsAndCharges

import config.FrontendAppConfig
import controllers.actions.{FakeIdentifierAction, IdentifierAction}
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.financialStatement.SchemeFS
import models.financialStatement.SchemeFSChargeType.PSS_AFT_RETURN
import models.requests.IdentifierRequest
import org.mockito.{ArgumentCaptor, Matchers}
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, times, verify, when}
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

import java.time.LocalDate
import scala.concurrent.Future

class PaymentsAndChargesOverdueControllerSpec
  extends ControllerSpecBase
    with NunjucksSupport
    with JsonMatchers
    with BeforeAndAfterEach {

  import PaymentsAndChargesOverdueControllerSpec._

  private def httpPathGET(startDate: String = startDate): String =
    controllers.financialStatement.paymentsAndCharges.routes.PaymentsAndChargesOverdueController.onPageLoad(srn, startDate).url

  private val mockSchemeService: SchemeService = mock[SchemeService]
  private val mockPaymentsAndChargesService: PaymentsAndChargesService = mock[PaymentsAndChargesService]
  private val application: Application = new GuiceApplicationBuilder()
    .overrides(
      Seq[GuiceableModule](
        bind[IdentifierAction].to[FakeIdentifierAction],
        bind[NunjucksRenderer].toInstance(mockRenderer),
        bind[FrontendAppConfig].toInstance(mockAppConfig),
        bind[PaymentsAndChargesService].toInstance(mockPaymentsAndChargesService)
      ): _*
    )
    .build()

  override def beforeEach: Unit = {
    super.beforeEach
    reset(
      mockSchemeService,
      mockRenderer,
      mockPaymentsAndChargesService
    )

    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockPaymentsAndChargesService.getPaymentsAndCharges(Matchers.eq(srn), any(), any())(any()))
      .thenReturn(emptyChargesTable)
    when(mockRenderer.render(any(), any())(any()))
      .thenReturn(Future.successful(Html("")))
  }

  private def expectedJson: JsObject = Json.obj(
    "heading" -> "Payments and charges for 1 October to 31 December 2020",
    "paymentAndChargesTable" -> emptyChargesTable,
    "schemeName" -> schemeDetails.schemeName,
    "returnUrl" -> dummyCall.url
  )

  "PaymentsAndChargesController for a GET" must {

    "return OK and the correct view with filtered payments and charges information for single period" in {
      when(mockPaymentsAndChargesService.getPaymentsFromCache(any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(schemeFSResponseSinglePeriod)))
      when(mockPaymentsAndChargesService.getOverdueCharges(any()))
        .thenReturn(schemeFSResponseSinglePeriod)
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      val result = route(application, httpGETRequest(httpPathGET())).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1))
        .render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual "financialStatement/paymentsAndCharges/paymentsAndChargesOverdue.njk"
      jsonCaptor.getValue must containJson(expectedJson)
    }

    "return OK and the correct view with filtered payments and charges information for multiple periods" in {
      when(mockPaymentsAndChargesService.getPaymentsFromCache(any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(schemeFSResponseMultiplePeriod)))
      when(mockPaymentsAndChargesService.getOverdueCharges(any()))
        .thenReturn(schemeFSResponseMultiplePeriod)
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      val result = route(application, httpGETRequest(httpPathGET())).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1))
        .render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual "financialStatement/paymentsAndCharges/paymentsAndChargesOverdue.njk"
      jsonCaptor.getValue must containJson(expectedJson)
    }

    "redirect to Session Expired page when there is no data for the selected year" in {
      when(mockPaymentsAndChargesService.getPaymentsFromCache(any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(Seq.empty)))
      when(mockPaymentsAndChargesService.getOverdueCharges(any()))
        .thenReturn(Seq.empty)
      val result = route(application, httpGETRequest(httpPathGET(startDate = "2022-10-01"))).value
      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
    }
  }
}

object PaymentsAndChargesOverdueControllerSpec {
  private val startDate = "2020-10-01"
  private val srn = "test-srn"
  private def createCharge(
                            startDate: String,
                            endDate: String,
                            chargeReference: String,
                            dueDate: Option[LocalDate] = Some(LocalDate.parse("2021-02-15"))
                          ): SchemeFS = {
    SchemeFS(
      chargeReference = chargeReference,
      chargeType = PSS_AFT_RETURN,
      dueDate = dueDate,
      totalAmount = 56432.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = 1029.05,
      accruedInterestTotal = 0.00,
      periodStartDate = LocalDate.parse(startDate),
      periodEndDate = LocalDate.parse(endDate)
    )
  }
  private val schemeFSResponseSinglePeriod: Seq[SchemeFS] = Seq(
    createCharge(
      startDate = "2020-10-01",
      endDate = "2020-12-31",
      chargeReference = "XY002610150184"
    ),
    createCharge(
      startDate = "2020-10-01",
      endDate = "2020-12-31",
      chargeReference = "AYU3494534632"
    ),
    createCharge(
      startDate = "2020-10-01",
      endDate = "2020-12-31",
      chargeReference = "XY002610150185"
    )
  )

  private val schemeFSResponseMultiplePeriod: Seq[SchemeFS] = Seq(
    createCharge(
      startDate = "2020-10-01",
      endDate = "2020-12-31",
      chargeReference = "XY002610150184"
    ),
    createCharge(
      startDate = "2020-10-01",
      endDate = "2020-12-31",
      chargeReference = "AYU3494534632"
    ),
    createCharge(
      startDate = "2021-01-01",
      endDate = "2021-03-31",
      chargeReference = "XY002610150185"
    )
  )
}




