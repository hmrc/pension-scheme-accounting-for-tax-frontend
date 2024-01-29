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

package controllers.financialOverview.scheme

import config.FrontendAppConfig
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, FakeIdentifierAction, IdentifierAction}
import controllers.base.ControllerSpecBase
import data.SampleData._
import helpers.FormatHelper
import matchers.JsonMatchers
import models.ChargeDetailsFilter.Overdue
import models.LocalDateBinder._
import models.financialStatement.PaymentOrChargeType.AccountingForTaxCharges
import models.financialStatement.SchemeFSChargeType.{PSS_AFT_RETURN, PSS_AFT_RETURN_INTEREST, PSS_OTC_AFT_RETURN, PSS_OTC_AFT_RETURN_INTEREST}
import models.financialStatement.{SchemeFSChargeType, SchemeFSDetail}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.BeforeAndAfterEach
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{route, _}
import services.financialOverview.scheme.{PaymentsAndChargesService, PaymentsCache}
import uk.gov.hmrc.nunjucks.NunjucksRenderer
import uk.gov.hmrc.viewmodels.NunjucksSupport
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import utils.AFTConstants._
import utils.DateHelper

import java.time.LocalDate
import scala.concurrent.Future

class PaymentsAndChargesInterestControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with BeforeAndAfterEach {

  import PaymentsAndChargesInterestControllerSpec._

  private def httpPathGET(startDate: LocalDate = QUARTER_START_DATE, index: String): String =
    routes.PaymentsAndChargesInterestController
      .onPageLoad(srn, pstr, startDate, index, AccountingForTaxCharges, Some(versionInt), Some(submittedDate), Overdue)
      .url

  private val paymentsCache: Seq[SchemeFSDetail] => PaymentsCache = schemeFSDetail => PaymentsCache(psaId, srn, schemeDetails, schemeFSDetail)

  val mockPaymentsAndChargesService: PaymentsAndChargesService = mock[PaymentsAndChargesService]

  private val application: Application = new GuiceApplicationBuilder()
    .overrides(
      Seq[GuiceableModule](
        bind[IdentifierAction].to[FakeIdentifierAction],
        bind[NunjucksRenderer].toInstance(mockRenderer),
        bind[FrontendAppConfig].toInstance(mockAppConfig),
        bind[PaymentsAndChargesService].toInstance(mockPaymentsAndChargesService),
        bind[AllowAccessActionProviderForIdentifierRequest].toInstance(mockAllowAccessActionProviderForIdentifierRequest)
      ): _*
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockAppConfig.schemeDashboardUrl(any(), any())).thenReturn(dummyCall.url)
    when(mockPaymentsAndChargesService.setPeriod(any(), any(), any()))
      .thenReturn("")
    when(mockPaymentsAndChargesService.getReturnLinkBasedOnJourney(any(), any())(any()))
      .thenReturn("")
    when(mockPaymentsAndChargesService.getReturnUrl(any(), any(), any(), any(), any(), any()))
      .thenReturn("")
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(play.twirl.api.Html("")))
  }

  private def insetTextWithAmountDueAndInterest(index: String): uk.gov.hmrc.viewmodels.Html = {
    uk.gov.hmrc.viewmodels.Html(
      s"<p class=govuk-body>${messages("paymentsAndCharges.interest.chargeReference.text1")}" +
        s" <span><a id='breakdown' class=govuk-link href=${
          routes.PaymentsAndChargeDetailsController
            .onPageLoad(srn, pstr, startDate, index, AccountingForTaxCharges, Some(versionInt), Some(submittedDate), Overdue)
            .url
        }>" +
        s" ${messages("paymentsAndCharges.interest.chargeReference.linkText")}</a></span>" +
        s" ${messages("paymentsAndCharges.interest.chargeReference.text2")}</p>"
    )
  }

  private def expectedJson(schemeFSDetail: SchemeFSDetail, chargeType: String, insetText: uk.gov.hmrc.viewmodels.Html, index: String): JsObject =
    Json.obj(
      fields = "chargeDetailsList" -> Seq(
        Row(
          key = Key(
            content = msg"financialPaymentsAndCharges.chargeReference",
            classes = Seq("govuk-!-padding-left-0", "govuk-!-width-one-half")
          ),
          value = Value(
            content = msg"paymentsAndCharges.chargeReference.toBeAssigned",
            classes = Seq("govuk-!-width-one-quarter")
          ),
          actions = Nil
        ),
        Row(
          key = Key(
            msg"paymentsAndCharges.interestFrom".withArgs(DateHelper.formatDateDMY(schemeFSDetail.periodEndDate.map(_.plusDays(46)))),
            classes = Seq("govuk-!-padding-left-0", "govuk-!-width-three-quarters", "govuk-!-font-weight-bold")
          ),
          value = Value(
            Literal(s"${FormatHelper.formatCurrencyAmountAsString(schemeFSDetail.accruedInterestTotal)}"),
            classes = Seq("govuk-!-width-one-quarter", "govuk-!-font-weight-bold")
          ),
          actions = Nil
        )
      ),
      "tableHeader" -> "",
      "schemeName" -> schemeName,
      "accruedInterest" -> schemeFSDetail.accruedInterestTotal,
      "chargeType" -> (chargeType + s" submission $version"),
      "insetText" -> insetText,
      "originalAmountUrl" -> routes.PaymentsAndChargeDetailsController
        .onPageLoad(srn, pstr, startDate, index, AccountingForTaxCharges, Some(versionInt), Some(submittedDate), Overdue)
        .url,
      "returnLinkBasedOnJourney" -> "",
      "returnUrl" -> ""
    )

  "PaymentsAndChargesInterestController" must {

    "return OK and the correct view for interest accrued for aft return charge if amount is due and interest is accruing for a GET" in {
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(schemeFSResponse)))

      val schemeFSDetail = createCharge(index = 1, chargeReference = "XY002610150184", chargeType = PSS_AFT_RETURN)
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      val result = route(application, httpGETRequest(httpPathGET(index = "1"))).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual "financialOverview/scheme/paymentsAndChargeInterest.njk"
      jsonCaptor.getValue must containJson(
        expectedJson(schemeFSDetail, PSS_AFT_RETURN_INTEREST.toString, insetTextWithAmountDueAndInterest("1"), "1"))
    }

    "return OK and the correct view for interest accrued for overseas transfer charge if amount is due and interest is accruing for a GET" in {
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(schemeFSResponse)))

      val schemeFSDetail = createCharge(index = 1, chargeReference = "XY002610150185", chargeType = PSS_OTC_AFT_RETURN)
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      val result = route(application, httpGETRequest(httpPathGET(index = "2"))).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual "financialOverview/scheme/paymentsAndChargeInterest.njk"

      jsonCaptor.getValue must containJson(
        expectedJson(schemeFSDetail, PSS_OTC_AFT_RETURN_INTEREST.toString, insetTextWithAmountDueAndInterest("2"), "2"))
    }

    "redirect to Session Expired page when there is no data for the selected charge reference for a GET" in {
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(Seq.empty)))
      val result = route(application, httpGETRequest(httpPathGET(index = "12"))).value
      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }
}

object PaymentsAndChargesInterestControllerSpec {
  private val srn = "test-srn"

  private def createCharge(index: Int, chargeReference: String, chargeType: SchemeFSChargeType): SchemeFSDetail = {
    SchemeFSDetail(
      index = index,
      chargeReference = chargeReference,
      chargeType = chargeType,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 56432.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = 1234.00,
      accruedInterestTotal = 2000.00,
      periodStartDate = Some(LocalDate.parse(QUARTER_START_DATE)),
      periodEndDate = Some(LocalDate.parse(QUARTER_END_DATE)),
      formBundleNumber = None,
      version = None,
      receiptDate = None,
      sourceChargeRefForInterest = None,
      sourceChargeInfo = None,
      documentLineItemDetails = Nil
    )
  }

  private val schemeFSResponse: Seq[SchemeFSDetail] = Seq(
    createCharge(1, chargeReference = "XY002610150184", PSS_AFT_RETURN),
    createCharge(2, chargeReference = "XY002610150185", PSS_OTC_AFT_RETURN)
  )
}
