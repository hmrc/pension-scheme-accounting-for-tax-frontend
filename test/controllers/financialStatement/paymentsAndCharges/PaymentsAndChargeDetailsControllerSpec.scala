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

package controllers.financialStatement.paymentsAndCharges

import config.FrontendAppConfig
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, FakeIdentifierAction, IdentifierAction}
import controllers.base.ControllerSpecBase
import controllers.financialStatement.paymentsAndCharges.routes._
import data.SampleData._
import helpers.FormatHelper
import matchers.JsonMatchers
import models.ChargeDetailsFilter.All
import models.LocalDateBinder._
import models.financialStatement.PaymentOrChargeType.AccountingForTaxCharges
import models.financialStatement.PsaFSChargeType.AFT_INITIAL_LFP
import models.financialStatement.SchemeFSChargeType.{PSS_AFT_RETURN, PSS_AFT_RETURN_INTEREST}
import models.financialStatement.{PsaFSDetail, SchemeFSDetail}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest._
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.test.Helpers.{route, _}
import services.paymentsAndCharges.PaymentsAndChargesService
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.HtmlContent
import uk.gov.hmrc.nunjucks.NunjucksRenderer
import utils.AFTConstants._
import utils.DateHelper
import utils.DateHelper.dateFormatterDMY
import views.html.financialStatement.paymentsAndCharges.PaymentsAndChargeDetailsView

import java.time.LocalDate
import scala.concurrent.Future

class PaymentsAndChargeDetailsControllerSpec
  extends ControllerSpecBase
    with JsonMatchers
    with BeforeAndAfterEach
    with RecoverMethods {

  import PaymentsAndChargeDetailsControllerSpec._

  private def httpPathGET(startDate: LocalDate = QUARTER_START_DATE, index: String): String =
    PaymentsAndChargeDetailsController.onPageLoad(srn, startDate, index, AccountingForTaxCharges, All).url

  private val mockPaymentsAndChargesService: PaymentsAndChargesService = mock[PaymentsAndChargesService]
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
    reset(mockRenderer)
    reset(mockPaymentsAndChargesService)
    when(mockAppConfig.schemeDashboardUrl(any(), any()))
      .thenReturn(dummyCall.url)
    when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(paymentsCache(schemeFSResponse)))
    when(mockPaymentsAndChargesService.getChargeDetailsForSelectedCharge(any())(any()))
      .thenReturn(Nil)
    when(mockRenderer.render(any(), any())(any()))
      .thenReturn(Future.successful(play.twirl.api.Html("")))
  }

  private def insetTextWithAmountDueAndInterest(schemeFSDetail: SchemeFSDetail, index: String = "1") = {
    HtmlContent(
      s"<h2 class=govuk-heading-s>${messages("paymentsAndCharges.chargeDetails.interestAccruing")}</h2>" +
        s"<p class=govuk-body>${
          messages("paymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate",
            schemeFSDetail.dueDate.getOrElse(LocalDate.now()).format(dateFormatterDMY))
        }" +
        s" <span>" +
        s"<a id='breakdown' class=govuk-link href=${
          controllers.financialStatement.paymentsAndCharges.routes.PaymentsAndChargesInterestController
            .onPageLoad(srn, schemeFSDetail.periodStartDate.get, index, AccountingForTaxCharges, All)
            .url
        }>" +
        s"${messages("paymentsAndCharges.chargeDetails.interest.paid")}</a></span></p>"
    )
  }

  private def insetTextWithNoAmountDue(schemeFSDetail: SchemeFSDetail): HtmlContent = {
    HtmlContent(
      s"<h2 class=govuk-heading-s>${messages("paymentsAndCharges.chargeDetails.interestAccrued")}</h2>" +
        s"<p class=govuk-body>${
          messages("paymentsAndCharges.chargeDetails.amount.paid.after.dueDate",
            schemeFSDetail.dueDate.getOrElse(LocalDate.now()).format(dateFormatterDMY))
        }</p>"
    )
  }

  "PaymentsAndChargesController" must {

    "return OK and the correct view with inset text linked to interest page if amount is due and interest is accruing for a GET" in {
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(Seq(
          createChargeWithAmountDueAndInterest("XY002610150183", amountDue = 1234.00),
          createChargeWithAmountDueAndInterest("XY002610150184", amountDue = 1234.00)
        ))))

      val schemeFSDetail = createChargeWithAmountDueAndInterest(chargeReference = "XY002610150184", amountDue = 1234.00)

      val view = application.injector.instanceOf[PaymentsAndChargeDetailsView].apply(
        chargeType = schemeFSDetail.chargeType.toString,
        isPaymentOverdue = true,
        tableHeader = messages("paymentsAndCharges.caption",
          DateHelper.formatStartDate(schemeFSDetail.periodStartDate),
          DateHelper.formatDateDMY(schemeFSDetail.periodEndDate)),
        chargeReferenceTextMessage = messages("paymentsAndCharges.chargeDetails.chargeReference", schemeFSDetail.chargeReference),
        chargeDetailsList = Nil,
        interest = schemeFSDetail.accruedInterestTotal,
        insetText = insetTextWithAmountDueAndInterest(schemeFSDetail),
        hintText = "",
        returnHistoryURL = controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, startDate).url,
        returnUrl = dummyCall.url,
        schemeName
      )(httpGETRequest(httpPathGET(index = "1")), messages)

      val result = route(application, httpGETRequest(httpPathGET(index = "1"))).value
      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return OK and the correct view with hint text linked to interest page if amount is due and interest is not accruing for a GET" in {
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(Seq(
          createChargeWithAmountDueAndInterestPayment("XY002610150188", interest = BigDecimal(0.00)),
          createChargeWithAmountDueAndInterestPayment("XY002610150189", interest = BigDecimal(0.00))
        ))))

      val schemeFSDetail = createChargeWithAmountDueAndInterestPayment(
        chargeReference = "XY002610150188",
        interest = BigDecimal(0.00)
      )

      val view = application.injector.instanceOf[PaymentsAndChargeDetailsView].apply(
        chargeType = schemeFSDetail.chargeType.toString,
        isPaymentOverdue = false,
        tableHeader = messages("paymentsAndCharges.caption",
          DateHelper.formatStartDate(schemeFSDetail.periodStartDate),
          DateHelper.formatDateDMY(schemeFSDetail.periodEndDate)),
        chargeReferenceTextMessage = messages("paymentsAndCharges.chargeDetails.chargeReference", schemeFSDetail.chargeReference),
        chargeDetailsList = Nil,
        interest = schemeFSDetail.accruedInterestTotal,
        insetText = insetTextWithAmountDueAndInterest(schemeFSDetail),
        hintText = messages("paymentsAndCharges.interest.hint"),
        returnHistoryURL = controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, startDate).url,
        returnUrl = dummyCall.url,
        schemeName
      )(httpGETRequest(httpPathGET(index = "0")), messages)

      val result = route(application, httpGETRequest(httpPathGET(index = "0"))).value
      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return OK and the correct view with inset text if amount is all paid and interest accrued has been created as another charge for a GET" in {
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(Seq(createChargeWithAmountDueAndInterest("XY002610150186"))
        )))

      val schemeFSDetail = createChargeWithAmountDueAndInterest(chargeReference = "XY002610150186")

      val view = application.injector.instanceOf[PaymentsAndChargeDetailsView].apply(
        chargeType = schemeFSDetail.chargeType.toString,
        isPaymentOverdue = false,
        tableHeader = messages("paymentsAndCharges.caption",
          DateHelper.formatStartDate(schemeFSDetail.periodStartDate),
          DateHelper.formatDateDMY(schemeFSDetail.periodEndDate)),
        chargeReferenceTextMessage = messages("paymentsAndCharges.chargeDetails.chargeReference", schemeFSDetail.chargeReference),
        chargeDetailsList = Nil,
        interest = schemeFSDetail.accruedInterestTotal,
        insetText = insetTextWithNoAmountDue(schemeFSDetail),
        hintText = "",
        returnHistoryURL = controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, startDate).url,
        returnUrl = dummyCall.url,
        schemeName
      )(httpGETRequest(httpPathGET(index = "0")), messages)

      val result = route(application, httpGETRequest(httpPathGET(index = "0"))).value
      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return OK and the correct view with no inset text if amount is all paid and no interest accrued for a GET" in {
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(Seq(createChargeWithAmountDueAndInterest("XY002610150187", interest = 0.00))
        )))
      val schemeFSDetail = createChargeWithAmountDueAndInterest(chargeReference = "XY002610150187", interest = 0.00)

      val req = httpGETRequest(httpPathGET(index = "0"))
      val view = application.injector.instanceOf[PaymentsAndChargeDetailsView].apply(
        chargeType = schemeFSDetail.chargeType.toString,
        isPaymentOverdue = false,
        tableHeader = messages("paymentsAndCharges.caption",
          DateHelper.formatStartDate(schemeFSDetail.periodStartDate),
          DateHelper.formatDateDMY(schemeFSDetail.periodEndDate)),
        chargeReferenceTextMessage = messages("paymentsAndCharges.chargeDetails.chargeReference", schemeFSDetail.chargeReference),
        chargeDetailsList = Nil,
        interest = schemeFSDetail.accruedInterestTotal,
        insetText = HtmlContent(""),
        hintText = "",
        returnHistoryURL = controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, startDate).url,
        returnUrl = dummyCall.url,
        schemeName
      )(req, messages)

      val result = route(application, req).value
      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return OK and the correct view with no inset text and correct chargeReference text if amount is in credit for a GET" in {
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(Seq(createChargeWithDeltaCredit())
        )))
      val schemeFSDetail = createChargeWithDeltaCredit()

      val view = application.injector.instanceOf[PaymentsAndChargeDetailsView].apply(
        chargeType = schemeFSDetail.chargeType.toString,
        isPaymentOverdue = false,
        tableHeader = messages("paymentsAndCharges.caption",
          DateHelper.formatStartDate(schemeFSDetail.periodStartDate),
          DateHelper.formatDateDMY(schemeFSDetail.periodEndDate)),
        chargeReferenceTextMessage = messages(
          "paymentsAndCharges.credit.information",
          s"${FormatHelper.formatCurrencyAmountAsString(schemeFSDetail.totalAmount.abs)}"
        ),
        chargeDetailsList = Nil,
        interest = schemeFSDetail.accruedInterestTotal,
        insetText = insetTextWithAmountDueAndInterest(schemeFSDetail),
        hintText = "",
        returnHistoryURL = controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, startDate).url,
        returnUrl = dummyCall.url,
        schemeName
      )(httpGETRequest(httpPathGET(index = "0")), messages)

      val result = route(application, httpGETRequest(httpPathGET(index = "0"))).value
      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "catch IndexOutOfBoundsException" in {
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(Seq(createChargeWithAmountDueAndInterest("XY002610150185"))
        )
        ))

      val result = route(application, httpGETRequest(httpPathGET(index = "1"))).value

      status(result) mustBe SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }

    "return charge details for XY002610150184 for startDate 2020-04-01 index 3" in {
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(Seq(
          createChargeWithAmountDueAndInterest("XY002610150181", amountDue = 1234.00),
          createChargeWithAmountDueAndInterest("XY002610150182", amountDue = 1234.00),
          createChargeWithAmountDueAndInterest("XY002610150183", amountDue = 1234.00),
          createChargeWithAmountDueAndInterest("XY002610150184", amountDue = 1234.00)
        )
        )
        ))

      val schemeFSDetail = createChargeWithAmountDueAndInterest("XY002610150184", amountDue = 1234.00)

      val view = application.injector.instanceOf[PaymentsAndChargeDetailsView].apply(
        chargeType = schemeFSDetail.chargeType.toString,
        isPaymentOverdue = true,
        tableHeader = messages("paymentsAndCharges.caption",
          DateHelper.formatStartDate(schemeFSDetail.periodStartDate),
          DateHelper.formatDateDMY(schemeFSDetail.periodEndDate)),
        chargeReferenceTextMessage = messages("paymentsAndCharges.chargeDetails.chargeReference", schemeFSDetail.chargeReference),
        chargeDetailsList = Nil,
        interest = schemeFSDetail.accruedInterestTotal,
        insetText = insetTextWithAmountDueAndInterest(schemeFSDetail, "3"),
        hintText = "",
        returnHistoryURL = controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, startDate).url,
        returnUrl = dummyCall.url,
        schemeName
      )(httpGETRequest(httpPathGET("2020-04-01", index = "3")), messages)

      val result = route(application, httpGETRequest(httpPathGET("2020-04-01", index = "3"))).value
      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return charge details for XY002610150181 for startDate 2020-04-01 index 0" in {
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(Seq(
          createChargeWithAmountDueAndInterest("XY002610150181", amountDue = 1234.00),
          createChargeWithAmountDueAndInterest("XY002610150182", amountDue = 1234.00),
          createChargeWithAmountDueAndInterest("XY002610150183", amountDue = 1234.00),
          createChargeWithAmountDueAndInterest("XY002610150184", amountDue = 1234.00)
        )
        )
        ))

      val schemeFSDetail = createChargeWithAmountDueAndInterest("XY002610150184", amountDue = 1234.00)

      val view = application.injector.instanceOf[PaymentsAndChargeDetailsView].apply(
        chargeType = schemeFSDetail.chargeType.toString,
        isPaymentOverdue = true,
        tableHeader = messages("paymentsAndCharges.caption",
          DateHelper.formatStartDate(schemeFSDetail.periodStartDate),
          DateHelper.formatDateDMY(schemeFSDetail.periodEndDate)),
        chargeReferenceTextMessage = messages("paymentsAndCharges.chargeDetails.chargeReference", schemeFSDetail.chargeReference),
        chargeDetailsList = Nil,
        interest = schemeFSDetail.accruedInterestTotal,
        insetText = insetTextWithAmountDueAndInterest(schemeFSDetail, "0"),
        hintText = "",
        returnHistoryURL = controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, startDate).url,
        returnUrl = dummyCall.url,
        schemeName
      )(httpGETRequest(httpPathGET("2020-04-01", index = "0")), messages)

      val result = route(application, httpGETRequest(httpPathGET("2020-04-01", index = "0"))).value
      status(result) mustEqual OK

      compareResultAndView(result, view)
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

  private def createChargeWithAmountDueAndInterest(
                                                    chargeReference: String,
                                                    amountDue: BigDecimal = 0.00,
                                                    interest: BigDecimal = 123.00
                                                  ): SchemeFSDetail = {
    SchemeFSDetail(
      index = 0,
      chargeReference = chargeReference,
      chargeType = PSS_AFT_RETURN,
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

  private def createChargeWithAmountDueAndInterestPayment(
                                                           chargeReference: String,
                                                           amountDue: BigDecimal = 0.00,
                                                           interest: BigDecimal
                                                         ): SchemeFSDetail = {
    SchemeFSDetail(
      index = 0,
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
      documentLineItemDetails = Nil
    )
  }

  private def createChargeWithDeltaCredit(): SchemeFSDetail = {
    SchemeFSDetail(
      index = 0,
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
    createChargeWithDeltaCredit(),
    createChargeWithAmountDueAndInterest(chargeReference = "XY002610150186"),
    createChargeWithAmountDueAndInterest(chargeReference = "XY002610150184", amountDue = 1234.00),
    createChargeWithAmountDueAndInterest(chargeReference = "XY002610150187", interest = 0.00),
    createChargeWithAmountDueAndInterestPayment(chargeReference = "XY002610150188", interest = 0.00)
  )
}
