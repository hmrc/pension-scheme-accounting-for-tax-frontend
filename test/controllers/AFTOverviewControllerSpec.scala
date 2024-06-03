/*
 * Copyright 2024 HM Revenue & Customs
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

import config.FrontendAppConfig
import controllers.AFTOverviewControllerSpec.{paymentsCache, schemeFSResponse}
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, FakeIdentifierAction, IdentifierAction}
import controllers.base.ControllerSpecBase
import data.SampleData.{dummyCall, emptyChargesTable, psaId, schemeDetails, schemeName}
import matchers.JsonMatchers
import models.SchemeDetails
import models.financialStatement.SchemeFSChargeType.PSS_AFT_RETURN
import models.financialStatement.SchemeFSDetail
import models.requests.IdentifierRequest
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.mockito.ArgumentCaptor
import org.scalatest.BeforeAndAfterEach
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers._
import play.twirl.api.Html
import services.{QuartersService, SchemeService}
import services.financialOverview.scheme.{PaymentsAndChargesService, PaymentsCache}
import uk.gov.hmrc.nunjucks.NunjucksRenderer
import uk.gov.hmrc.viewmodels.NunjucksSupport

import java.time.LocalDate
import scala.concurrent.Future


class AFTOverviewControllerSpec extends ControllerSpecBase  with NunjucksSupport with JsonMatchers with BeforeAndAfterEach {

  private def httpPathGET(srn: String): String = {
    routes.AFTOverviewController.onPageLoad(srn).url
  }

  private val mockPaymentsAndChargesService: PaymentsAndChargesService = mock[PaymentsAndChargesService]
  private val mockSchemeService: SchemeService = mock[SchemeService]
  private val mockQuartersService: QuartersService = mock[QuartersService]

  private val application: Application = new GuiceApplicationBuilder()
    .overrides(
      Seq[GuiceableModule](
        bind[IdentifierAction].to[FakeIdentifierAction],
        bind[NunjucksRenderer].toInstance(mockRenderer),
        bind[FrontendAppConfig].toInstance(mockAppConfig),
        bind[PaymentsAndChargesService].toInstance(mockPaymentsAndChargesService),
        bind[SchemeService].toInstance(mockSchemeService),
        bind[QuartersService].toInstance(mockQuartersService),
        bind[AllowAccessActionProviderForIdentifierRequest].toInstance(mockAllowAccessActionProviderForIdentifierRequest)
      ): _*
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockRenderer)
    reset(mockPaymentsAndChargesService)
    reset(mockQuartersService)
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)

    when(mockPaymentsAndChargesService.getDueCharges(any()))
      .thenReturn(schemeFSResponse)
    when(mockQuartersService.getInProgressQuarters(any(), any())(any(), any()))
      .thenReturn(Future.successful(Seq.empty))
    when(mockQuartersService.getPastYears(any())(any(), any()))
      .thenReturn(Future.successful(Seq.empty))
    when(mockPaymentsAndChargesService.getInterestCharges(any()))
      .thenReturn(schemeFSResponse)
    when(mockPaymentsAndChargesService.getPaymentsAndCharges(any(), any(), any())(any())).thenReturn(emptyChargesTable)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
  }


  "AFT Overview Controller" must {

    "must return OK and the correct view for a GET" in {

      val srn = "test-srn"
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val aftOverviewTemplate = "aftOverview.njk"

      when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(SchemeDetails(schemeName, "", "", None)))
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(schemeFSResponse)))

      val result = route(application, httpGETRequest(httpPathGET(srn))).value
      status(result) mustEqual OK
      
      verify(mockRenderer, times(1)).render(templateCaptor.capture(), any())(any())

      templateCaptor.getValue mustEqual aftOverviewTemplate
    }


    "return Success page when paymentsAndChargesService fails" in {
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
        .thenReturn(Future.failed(new RuntimeException("Test exception")))

      when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(SchemeDetails(schemeName, "", "", None)))

      val srn: String = "S2012345678"

      val result = route(application, httpGETRequest(httpPathGET(srn))).value

      status(result) mustEqual OK

    }

  }
}

object AFTOverviewControllerSpec {
  private val srn = "test-srn"

  private def createCharge(startDate: String, endDate: String, chargeReference: String): SchemeFSDetail = {
    SchemeFSDetail(
      index = 0,
      chargeReference = chargeReference,
      chargeType = PSS_AFT_RETURN,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 56432.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = 1029.05,
      accruedInterestTotal = 0.00,
      periodStartDate = Some(LocalDate.parse(startDate)),
      periodEndDate = Some(LocalDate.parse(endDate)),
      formBundleNumber = None,
      version = None,
      receiptDate = None,
      sourceChargeRefForInterest = None,
      sourceChargeInfo = None,
      documentLineItemDetails = Nil
    )
  }

  private val schemeFSResponse: Seq[SchemeFSDetail] = Seq(
    createCharge(startDate = "2020-04-01", endDate = "2020-06-30", chargeReference = "XY002610150184"),
    createCharge(startDate = "2020-01-01", endDate = "2020-03-31", chargeReference = "AYU3494534632"),
    createCharge(startDate = "2021-04-01", endDate = "2021-06-30", chargeReference = "XY002610150185")
  )

  private val paymentsCache: Seq[SchemeFSDetail] => PaymentsCache = schemeFSDetail => PaymentsCache(psaId, srn, schemeDetails, schemeFSDetail)
}