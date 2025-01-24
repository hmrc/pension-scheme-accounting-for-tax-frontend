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
import matchers.JsonMatchers
import models.ChargeDetailsFilter.{All, Overdue}
import models.LocalDateBinder._
import models.financialStatement.PaymentOrChargeType.AccountingForTaxCharges
import models.financialStatement.PsaFSChargeType.AFT_INITIAL_LFP
import models.financialStatement.SchemeFSChargeType.{PSS_AFT_RETURN, PSS_AFT_RETURN_INTEREST}
import models.financialStatement._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest._
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{route, _}
import services.financialOverview.scheme.{PaymentsAndChargesService, PaymentsCache}
import uk.gov.hmrc.govukfrontend.views.Aliases.Table
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.HtmlContent
import utils.AFTConstants._
import utils.DateHelper.dateFormatterDMY
import viewmodels.ChargeDetailsViewModel
import views.html.financialOverview.scheme.{PaymentsAndChargeDetailsNewView, PaymentsAndChargeDetailsView}

import java.time.LocalDate
import scala.concurrent.Future

class PaymentsAndChargeDetailsControllerSpec
  extends ControllerSpecBase
    with JsonMatchers
    with BeforeAndAfterEach
    with RecoverMethods {

  //scalastyle.off: magic.number

  import PaymentsAndChargeDetailsControllerSpec._

  private val paymentsCache: Seq[SchemeFSDetail] => PaymentsCache = schemeFSDetail => PaymentsCache(psaId, srn, schemeDetails, schemeFSDetail)
  private def httpPathGET(startDate: LocalDate = QUARTER_START_DATE, index: String): String =
    routes.PaymentsAndChargeDetailsController
      .onPageLoad(srn, startDate, index, AccountingForTaxCharges, Some(versionInt), Some(submittedDate), Overdue)
      .url

  private val emptyChargeAmountTable: Table = Table()
  private val mockPaymentsAndChargesService: PaymentsAndChargesService = mock[PaymentsAndChargesService]
  private val application: Application = new GuiceApplicationBuilder()
    .overrides(
      Seq[GuiceableModule](
        bind[IdentifierAction].to[FakeIdentifierAction],
        bind[FrontendAppConfig].toInstance(mockAppConfig),
        bind[PaymentsAndChargesService].toInstance(mockPaymentsAndChargesService),
        bind[AllowAccessActionProviderForIdentifierRequest].toInstance(mockAllowAccessActionProviderForIdentifierRequest)
      ): _*
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPaymentsAndChargesService)
    when(mockAppConfig.schemeDashboardUrl(any(), any()))
      .thenReturn(dummyCall.url)
    when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(paymentsCache(schemeFSResponse)))
    when(mockPaymentsAndChargesService.getChargeDetailsForSelectedCharge(any(), any(), any())(any()))
      .thenReturn(Nil)
    when(mockPaymentsAndChargesService.setPeriod(any(), any(), any()))
      .thenReturn("")
    when(mockPaymentsAndChargesService.getReturnLinkBasedOnJourney(any(), any())(any()))
      .thenReturn("")
    when(mockPaymentsAndChargesService.getReturnUrl(any(), any(), any(), any(), any()))
      .thenReturn("")
  }

  private def insetTextWithAmountDueAndInterest(schemeFSDetail: SchemeFSDetail): HtmlContent = {
    HtmlContent(
      s"<h2 class=govuk-heading-s>${messages("paymentsAndCharges.chargeDetails.interestAccruing")}</h2>" +
        s"<p class=govuk-body>${messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line1")}" +
        s" <span class=govuk-!-font-weight-bold>${
          messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line2",
            schemeFSDetail.accruedInterestTotal)
        }</span>" +
        s" <span>${
          messages(
            "financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line3",
            schemeFSDetail.dueDate.getOrElse(LocalDate.now()).format(dateFormatterDMY)
          )
        }<span>" +
        s"<p class=govuk-body><span><a id='breakdown' class=govuk-link href=${
          routes.PaymentsAndChargesInterestController
            .onPageLoad(srn, schemeFSDetail.periodStartDate.get, "1", AccountingForTaxCharges, Some(versionInt), Some(submittedDate), Overdue)
            .url
        }>" +
        s" ${messages("paymentsAndCharges.chargeDetails.interest.paid")}</a></span></p>"
    )
  }

  private def insetText(schemeFSDetail: SchemeFSDetail): HtmlContent = {
    val interestUrl = "/manage-pension-scheme-accounting-for-tax/test-srn/financial-overview/accounting-for-tax/" +
      "2020-04-01/1/2016-12-17/1/interest/overdue-charge-details"

    HtmlContent(
      s"<h2 class=govuk-heading-s>${messages("paymentsAndCharges.chargeDetails.interestAccruing")}</h2>" +
        s"<p class=govuk-body>${messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line1")}" +
        s" <span class=govuk-!-font-weight-bold>${
          messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line2",
            schemeFSDetail.accruedInterestTotal)
        }</span>" +
        s" <span>${messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line3", "15 February 2020")}<span>" +
        s"<p class=govuk-body><span><a id='breakdown' class=govuk-link href=$interestUrl>" +
        s" ${messages("paymentsAndCharges.chargeDetails.interest.paid")}</a></span></p>"
    )
  }

  private def insetTextForInterestWithQuarter(schemeFSDetail: SchemeFSDetail): HtmlContent = {
    HtmlContent(
      s"<p class=govuk-body>${messages("financialPaymentsAndCharges.interest.chargeReference.text2", schemeFSDetail.chargeType.toString.toLowerCase())}</p>" +
        s"<p class=govuk-body><a id='breakdown' class=govuk-link href=${
          routes.PaymentsAndChargeDetailsController
            .onPageLoad(srn, schemeFSDetail.periodStartDate.get, "1", AccountingForTaxCharges, Some(versionInt), Some(submittedDate), All)
            .url
        }>" +
        s"${messages("financialPaymentsAndCharges.interest.chargeReference.linkText")}</a></p>"
    )
  }

  private def expectedJson(
                            schemeFSDetail: SchemeFSDetail,
                            insetText: uk.gov.hmrc.viewmodels.Html,
                            isPaymentOverdue: Boolean = false,
                            optHint: Option[String] = None
                          ): JsObject = {
    val commonJson = Json.obj(
      "chargeDetailsList" -> None,
      "tableHeader" -> "",
      "schemeName" -> schemeName,
      "chargeType" -> (schemeFSDetail.chargeType.toString + s" submission $version"),
      "versionValue" -> s" submission $version",
      "isPaymentOverdue" -> isPaymentOverdue,
      "insetText" -> insetText,
      "interest" -> schemeFSDetail.accruedInterestTotal,
      "returnLinkBasedOnJourney" -> "",
      "returnUrl" -> "",
      "returnHistoryURL" -> "/manage-pension-scheme-accounting-for-tax/test-srn/2020-04-01/submission/1/summary"
    )
    optHint match {
      case Some(_) => commonJson ++ Json.obj("hintText" -> messages("paymentsAndCharges.interest.hint"))
      case _ => commonJson
    }
  }

  "PaymentsAndChargesController" must {

    "return OK and the correct view if financial toggles  are switched on" in {
      when(mockAppConfig.podsNewFinancialCredits).thenReturn(true)
    }

    "return OK and the correct view with inset text linked to interest page if amount is due and interest is accruing for a GET" in {
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(Seq(
          createChargeWithAmountDueAndInterest(index = 1, "XY002610150183", amountDue = 1234.00),
          createChargeWithAmountDueAndInterest(index = 2, "XY002610150184", amountDue = 1234.00)
        ))))
      when(mockAppConfig.podsNewFinancialCredits).thenReturn(false)
      val schemeFSDetail = createChargeWithAmountDueAndInterest(index = 1, chargeReference = "XY002610150184", amountDue = 1234.00)

      val request = httpGETRequest(httpPathGET(index = "1"))

      val view = application.injector.instanceOf[PaymentsAndChargeDetailsView].apply(
        model = ChargeDetailsViewModel(
          chargeDetailsList = Nil,
          tableHeader = Some(""),
          schemeName = schemeName,
          chargeType = schemeFSDetail.chargeType.toString + s" submission $version",
          versionValue = Some(s" submission $version"),
          isPaymentOverdue = true,
          insetText = insetText(schemeFSDetail),
          interest = Some(schemeFSDetail.accruedInterestTotal),
          returnLinkBasedOnJourney = "",
          returnUrl = "",
          returnHistoryUrl = "/manage-pension-scheme-accounting-for-tax/test-srn/2020-04-01/submission/1/summary",
          paymentDueAmount = Some("0"),
          paymentDueDate = Some("0"),
          chargeAmountDetails = Some(emptyChargeAmountTable),
          hintText = Some("")
        )
      )(messages, request)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)

    }

    "return OK and the correct view with hint text linked to interest page if amount is due and interest is not accruing for a GET" in {
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(Seq(
          createChargeWithAmountDueAndInterestPayment(index = 1, "XY002610150188", interest = BigDecimal(0.00)),
          createChargeWithAmountDueAndInterestPayment(index = 2, "XY002610150189", interest = BigDecimal(0.00))
        ))))

      val schemeFSDetail = createChargeWithAmountDueAndInterestPayment(
        index = 1,
        chargeReference = "XY002610150188",
        interest = BigDecimal(0.00)
      )

      val request =  httpGETRequest(httpPathGET(index = "1"))

      val view = application.injector.instanceOf[PaymentsAndChargeDetailsView].apply(
        model = ChargeDetailsViewModel(
          chargeDetailsList = Nil,
          tableHeader = Some(""),
          schemeName = schemeName,
          chargeType = schemeFSDetail.chargeType.toString + s" submission $version",
          versionValue = Some(s" submission $version"),
          isPaymentOverdue = false,
          insetText = HtmlContent(""),
          interest = Some(schemeFSDetail.accruedInterestTotal),
          returnLinkBasedOnJourney = "",
          returnUrl = "",
          returnHistoryUrl = "/manage-pension-scheme-accounting-for-tax/test-srn/2020-04-01/submission/1/summary",
          paymentDueAmount = Some("0"),
          paymentDueDate = Some("0"),
          chargeAmountDetails = Some(emptyChargeAmountTable),
          hintText = Some(messages("paymentsAndCharges.interest.hint"))
        )
      )(messages, request)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)

    }

    "return OK and the correct view with inset text linked to original charge page if linked interest is present and Quarter is applicable for a GET" in {
      val sourceChargeInfo = SchemeSourceChargeInfo(
        index = 1,
        version = Some(1),
        receiptDate = Some(LocalDate.parse("2016-12-17")),
        periodStartDate = Some(LocalDate.parse(QUARTER_START_DATE)),
        periodEndDate = Some(LocalDate.parse(QUARTER_END_DATE))
      )

      val schemeFSDetail = createChargeWithSourceChargeReference(
        index = 2,
        chargeReference = "XY002610150184",
        sourceChargeReference = "XY002610150183",
        sourceChargeInfo = Some(sourceChargeInfo),
        amountDue = 123.00
      )

      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
        .thenReturn(
          Future.successful(
            paymentsCache(
              Seq(
                createChargeWithAmountDueAndInterest(index = 1, "XY002610150183", amountDue = 1234.00),
                schemeFSDetail
              ))))

      val result = route(application, httpGETRequest(httpPathGET(index = "2"))).value
      status(result) mustEqual OK

      val view = application.injector.instanceOf[PaymentsAndChargeDetailsView].apply(
        model = ChargeDetailsViewModel(
          chargeDetailsList = Nil,
          tableHeader = Some(""),
          schemeName = schemeName,
          chargeType = schemeFSDetail.chargeType.toString + s" submission $version",
          versionValue = Some(s" submission $version"),
          isPaymentOverdue = true,
          insetText = insetTextForInterestWithQuarter(schemeFSDetail),
          interest = Some(schemeFSDetail.accruedInterestTotal),
          returnLinkBasedOnJourney = "",
          returnUrl = "",
          returnHistoryUrl = "/manage-pension-scheme-accounting-for-tax/test-srn/2020-04-01/submission/1/summary",
          paymentDueAmount = Some("0"),
          paymentDueDate = Some("0"),
          chargeAmountDetails = Some(emptyChargeAmountTable),
          hintText = Some("")
        )
      )(messages, fakeRequest)

      compareResultAndView(result, view)
    }

    "return OK and the correct view with no inset text if amount is all paid and no interest accrued for a GET" in {
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(Seq(createChargeWithAmountDueAndInterest(index = 1, "XY002610150187", interest = 0.00)))))
      when(mockAppConfig.podsNewFinancialCredits).thenReturn(false)
      val schemeFSDetail = createChargeWithAmountDueAndInterest(index = 1, chargeReference = "XY002610150187", interest = 0.00)
      val result = route(application, httpGETRequest(httpPathGET(index = "1"))).value

      status(result) mustEqual OK

      val view = application.injector.instanceOf[PaymentsAndChargeDetailsView].apply(
        model = ChargeDetailsViewModel(
          chargeDetailsList = Nil,
          tableHeader = Some(""),
          schemeName = schemeName,
          chargeType = schemeFSDetail.chargeType.toString + s" submission $version",
          versionValue = Some(s" submission $version"),
          isPaymentOverdue = false,
          insetText = HtmlContent(""),
          interest = Some(schemeFSDetail.accruedInterestTotal),
          returnLinkBasedOnJourney = "",
          returnUrl = "",
          returnHistoryUrl = "/manage-pension-scheme-accounting-for-tax/test-srn/2020-04-01/submission/1/summary",
          paymentDueAmount = Some("0"),
          paymentDueDate = Some("0"),
          chargeAmountDetails = Some(emptyChargeAmountTable),
          hintText = Some("")
        )
      )(messages, fakeRequest)

      compareResultAndView(result, view)

    }

    "catch IndexOutOfBoundsException" in {
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(Seq(createChargeWithAmountDueAndInterest(index = 1, "XY002610150185")))))

      val result = route(application, httpGETRequest(httpPathGET(index = "2"))).value

      status(result) mustBe SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }
}

object PaymentsAndChargeDetailsControllerSpec {
  private val srn = "test-srn"

  def psaFS(chargeReference: String): PsaFSDetail =
    PsaFSDetail(
      index = 0,
      chargeReference = chargeReference,
      chargeType = AFT_INITIAL_LFP,
      dueDate = Some(LocalDate.parse("2020-07-15")),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 0.00,
      amountDue = 1029.05,
      periodStartDate = LocalDate.parse("2020-04-01"),
      periodEndDate = LocalDate.parse("2020-06-30"),
      pstr = "24000040IN",
      sourceChargeRefForInterest = None,
      psaSourceChargeInfo = None,
      documentLineItemDetails = Nil
    )

  private def createChargeWithAmountDueAndInterest(index: Int,
                                                   chargeReference: String,
                                                   chargeType: SchemeFSChargeType = PSS_AFT_RETURN,
                                                   amountDue: BigDecimal = 0.00,
                                                   interest: BigDecimal = 123.00): SchemeFSDetail = {
    SchemeFSDetail(
      index = index,
      chargeReference = chargeReference,
      chargeType = chargeType,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 56432.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = amountDue,
      accruedInterestTotal = interest,
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

  private def createChargeWithSourceChargeReference(index: Int,
                                                    chargeReference: String,
                                                    sourceChargeReference: String,
                                                    sourceChargeInfo: Option[SchemeSourceChargeInfo],
                                                    chargeType: SchemeFSChargeType = PSS_AFT_RETURN_INTEREST,
                                                    amountDue: BigDecimal,
                                                    interest: BigDecimal = 123.00): SchemeFSDetail = {
    SchemeFSDetail(
      index = index,
      chargeReference = chargeReference,
      chargeType = chargeType,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 56432.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = amountDue,
      accruedInterestTotal = interest,
      periodStartDate = Some(LocalDate.parse(QUARTER_START_DATE)),
      periodEndDate = Some(LocalDate.parse(QUARTER_END_DATE)),
      formBundleNumber = None,
      version = None,
      receiptDate = None,
      sourceChargeRefForInterest = Some(sourceChargeReference),
      sourceChargeInfo = sourceChargeInfo,
      documentLineItemDetails = Nil
    )
  }

  private def createChargeWithAmountDueAndInterestPayment(index: Int,
                                                          chargeReference: String,
                                                          amountDue: BigDecimal = 0.00,
                                                          interest: BigDecimal): SchemeFSDetail = {
    SchemeFSDetail(
      index = index,
      chargeReference = chargeReference,
      chargeType = PSS_AFT_RETURN_INTEREST,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 56432.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = amountDue,
      accruedInterestTotal = interest,
      periodStartDate = Some(LocalDate.parse(QUARTER_START_DATE)),
      periodEndDate = Some(LocalDate.parse(QUARTER_END_DATE)),
      formBundleNumber = None,
      version = None,
      receiptDate = None,
      sourceChargeRefForInterest = None,
      sourceChargeInfo = None,
      documentLineItemDetails = Seq(
        DocumentLineItemDetail(
          clearingReason = Some(FSClearingReason.CLEARED_WITH_PAYMENT),
          clearingDate = Some(LocalDate.parse("2020-06-30")),
          paymDateOrCredDueDate = Some(LocalDate.parse("2020-04-24")),
          clearedAmountItem = BigDecimal(0.00)
        ))
    )
  }

  private def createChargeWithDeltaCredit(index: Int): SchemeFSDetail = {
    SchemeFSDetail(
      index = index,
      chargeReference = "XY002610150185",
      chargeType = PSS_AFT_RETURN,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = -20000.00,
      outstandingAmount = 0.00,
      stoodOverAmount = 0.00,
      amountDue = 0.00,
      accruedInterestTotal = 0.00,
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
    createChargeWithDeltaCredit(index = 1),
    createChargeWithAmountDueAndInterest(index = 2, chargeReference = "XY002610150186"),
    createChargeWithAmountDueAndInterest(index = 3, chargeReference = "XY002610150184", amountDue = 1234.00),
    createChargeWithAmountDueAndInterest(index = 4, chargeReference = "XY002610150187", interest = 0.00),
    createChargeWithAmountDueAndInterestPayment(index = 5, chargeReference = "XY002610150188", interest = 0.00)
  )
}
