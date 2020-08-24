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
import helpers.FormatHelper
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.financialStatement.SchemeFSChargeType.{PSS_AFT_RETURN, PSS_AFT_RETURN_INTEREST, PSS_OTC_AFT_RETURN, PSS_OTC_AFT_RETURN_INTEREST}
import models.financialStatement.{SchemeFS, SchemeFSChargeType}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{route, _}
import services.SchemeService
import uk.gov.hmrc.nunjucks.NunjucksRenderer
import uk.gov.hmrc.viewmodels.NunjucksSupport
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import utils.AFTConstants._
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}

import scala.concurrent.Future

class PaymentsAndChargesInterestControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with BeforeAndAfterEach {

  import PaymentsAndChargesInterestControllerSpec._

  private def httpPathGET(startDate: LocalDate = QUARTER_START_DATE, index: String): String =
    controllers.paymentsAndCharges.routes.PaymentsAndChargesInterestController.onPageLoad(srn, startDate, index).url

  private val mockSchemeService: SchemeService = mock[SchemeService]
  private val mockFSConnector: FinancialStatementConnector = mock[FinancialStatementConnector]
  private val mockIFConnector: FinancialInfoCacheConnector = mock[FinancialInfoCacheConnector]
  private val application: Application = new GuiceApplicationBuilder()
    .overrides(
      Seq[GuiceableModule](
        bind[IdentifierAction].to[FakeIdentifierAction],
        bind[NunjucksRenderer].toInstance(mockRenderer),
        bind[FrontendAppConfig].toInstance(mockAppConfig),
        bind[SchemeService].toInstance(mockSchemeService),
        bind[FinancialStatementConnector].toInstance(mockFSConnector),
        bind[FinancialInfoCacheConnector].toInstance(mockIFConnector)
      ): _*
    )
    .build()

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockSchemeService, mockFSConnector, mockRenderer, mockIFConnector)
    when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(dummyCall.url)
    when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any())).thenReturn(Future.successful(schemeDetails))
    when(mockFSConnector.getSchemeFS(any())(any(), any())).thenReturn(Future.successful(schemeFSResponse))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(play.twirl.api.Html("")))
  }

  private def expectedJson(schemeFS: SchemeFS, chargeType: String, index: String): JsObject = Json.obj(
    fields = "chargeDetailsList" -> Seq(
      Row(
        key = Key(msg"paymentsAndCharges.interest", classes = Seq("govuk-!-padding-left-0", "govuk-!-width-three-quarters")),
        value = Value(
          Literal(s"${FormatHelper.formatCurrencyAmountAsString(schemeFS.accruedInterestTotal)}"),
          classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")
        ),
        actions = Nil
      ),
      Row(
        key = Key(
          msg"paymentsAndCharges.interestDue".withArgs(LocalDate.now().format(dateFormatterDMY)),
          classes = Seq("govuk-table__cell--numeric", "govuk-!-padding-right-0", "govuk-!-width-three-quarters", "govuk-!-font-weight-bold")
        ),
        value = Value(
          Literal(s"${FormatHelper.formatCurrencyAmountAsString(schemeFS.accruedInterestTotal)}"),
          classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric", "govuk-!-font-weight-bold")
        ),
        actions = Nil
      )
    ),
    "tableHeader" -> messages("paymentsAndCharges.caption",
      schemeFS.periodStartDate.format(dateFormatterStartDate),
      schemeFS.periodEndDate.format(dateFormatterDMY)),
    "schemeName" -> schemeName,
    "accruedInterest" -> schemeFS.accruedInterestTotal,
    "chargeType" -> chargeType,
    "originalAmountUrl" -> controllers.paymentsAndCharges.routes.PaymentsAndChargeDetailsController
      .onPageLoad(srn, schemeFS.periodStartDate, index)
      .url,
    "returnUrl" -> dummyCall.url
  )

  "PaymentsAndChargesInterestController" must {

    "return OK and the correct view for interest accrued for aft return charge if amount is due and interest is accruing for a GET" in {
      when(mockIFConnector.fetch(any(), any()))
        .thenReturn(Future.successful(Some(chargeRefs)))

      val schemeFS = createCharge(chargeReference = "XY002610150184", chargeType = PSS_AFT_RETURN)
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      val result = route(application, httpGETRequest(httpPathGET(index = "0"))).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual "paymentsAndCharges/paymentsAndChargeInterest.njk"
      jsonCaptor.getValue must containJson(expectedJson(schemeFS, PSS_AFT_RETURN_INTEREST.toString, "0"))
    }

    "return OK and the correct view for interest accrued for overseas transfer charge if amount is due and interest is accruing for a GET" in {
      when(mockIFConnector.fetch(any(), any()))
        .thenReturn(Future.successful(Some(chargeRefs)))

      val schemeFS = createCharge(chargeReference = "XY002610150185", chargeType = PSS_OTC_AFT_RETURN)
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      val result = route(application, httpGETRequest(httpPathGET(index = "1"))).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual "paymentsAndCharges/paymentsAndChargeInterest.njk"
      jsonCaptor.getValue must containJson(expectedJson(schemeFS, PSS_OTC_AFT_RETURN_INTEREST.toString, "1"))
    }

    "redirect to Session Expired page when there is no data for the selected charge reference for a GET" in {
      when(mockIFConnector.fetch(any(), any()))
        .thenReturn(Future.successful(Some(Json.obj("chargeRefs" -> Seq("XY002610150186")))))

      val result = route(application, httpGETRequest(httpPathGET(index = "0"))).value
      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
    }

    "redirect to Session Expired page when there is no charge reference held in FI Cache for a GET" in {
      when(mockIFConnector.fetch(any(), any()))
        .thenReturn(Future.successful(None))

      val result = route(application, httpGETRequest(httpPathGET(index = "0"))).value
      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
    }
  }
}

object PaymentsAndChargesInterestControllerSpec {
  private val srn = "test-srn"

  private def createCharge(chargeReference: String, chargeType: SchemeFSChargeType): SchemeFS = {
    SchemeFS(
      chargeReference = chargeReference,
      chargeType = chargeType,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 56432.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = 1234.00,
      accruedInterestTotal = 2000.00,
      periodStartDate = LocalDate.parse(QUARTER_START_DATE),
      periodEndDate = LocalDate.parse(QUARTER_END_DATE)
    )
  }

  private val chargeRefs: JsObject = Json.obj(
    "chargeRefs" -> Seq(
      "XY002610150184",
      "XY002610150185"
    ))

  private val schemeFSResponse: Seq[SchemeFS] = Seq(
    createCharge(chargeReference = "XY002610150184", PSS_AFT_RETURN),
    createCharge(chargeReference = "XY002610150185", PSS_OTC_AFT_RETURN)
  )
}
