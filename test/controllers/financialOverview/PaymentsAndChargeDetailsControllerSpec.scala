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

package controllers.financialOverview

import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, FakeIdentifierAction, IdentifierAction}
import controllers.base.ControllerSpecBase
import controllers.financialOverview.routes._
import data.SampleData._
import matchers.JsonMatchers
import models.ChargeDetailsFilter.Overdue
import models.LocalDateBinder._
import models.financialStatement.PaymentOrChargeType.AccountingForTaxCharges
import models.financialStatement.PsaFSChargeType.AFT_INITIAL_LFP
import models.financialStatement.SchemeFSChargeType.{PSS_AFT_RETURN, PSS_AFT_RETURN_INTEREST}
import models.financialStatement.{PsaFS, SchemeFS, SchemeFSChargeType}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.scalatest._
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{route, _}
import services.financialOverview.{PaymentsAndChargesService, PaymentsCache}
import uk.gov.hmrc.nunjucks.NunjucksRenderer
import uk.gov.hmrc.viewmodels.{Html, NunjucksSupport}
import utils.AFTConstants._
import utils.DateHelper.dateFormatterDMY

import java.time.LocalDate
import scala.concurrent.Future

class PaymentsAndChargeDetailsControllerSpec
  extends ControllerSpecBase
    with NunjucksSupport
    with JsonMatchers
    with BeforeAndAfterEach
    with RecoverMethods {

  import PaymentsAndChargeDetailsControllerSpec._

  private val paymentsCache: Seq[SchemeFS] => PaymentsCache = schemeFS => PaymentsCache(psaId, srn, schemeDetails, schemeFS)

  private def httpPathGET(startDate: LocalDate = QUARTER_START_DATE, index: String): String =
    PaymentsAndChargeDetailsController.onPageLoad(srn, pstr, startDate, index, AccountingForTaxCharges, Some(versionInt), Some(submittedDate), Overdue).url

  private val mockPaymentsAndChargesService: PaymentsAndChargesService = mock[PaymentsAndChargesService]
  private val application: Application = new GuiceApplicationBuilder()
    .overrides(
      Seq[GuiceableModule](
        bind[IdentifierAction].to[FakeIdentifierAction],
        bind[NunjucksRenderer].toInstance(mockRenderer),
        bind[PaymentsAndChargesService].toInstance(mockPaymentsAndChargesService),
        bind[AllowAccessActionProviderForIdentifierRequest].toInstance(mockAllowAccessActionProviderForIdentifierRequest)
      ): _*
    )
    .build()

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockRenderer, mockPaymentsAndChargesService)
    when(mockAppConfig.schemeDashboardUrl(any(), any()))
      .thenReturn(dummyCall.url)
    when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(paymentsCache(schemeFSResponse)))
    when(mockPaymentsAndChargesService.getChargeDetailsForSelectedCharge(any(), any(), any()))
      .thenReturn(Nil)
    when(mockRenderer.render(any(), any())(any()))
      .thenReturn(Future.successful(play.twirl.api.Html("")))
  }

  private def insetTextWithAmountDueAndInterest(schemeFS: SchemeFS): uk.gov.hmrc.viewmodels.Html = {
    uk.gov.hmrc.viewmodels.Html(
      s"<h2 class=govuk-heading-s>${messages("paymentsAndCharges.chargeDetails.interestAccruing")}</h2>" +
        s"<p class=govuk-body>${
          messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line1")}" +
        s" <span class=govuk-!-font-weight-bold>${messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line2", schemeFS.accruedInterestTotal)}</span>" +
        s" <span>${messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line3", schemeFS.dueDate.getOrElse(LocalDate.now()).format(dateFormatterDMY))}<span>" +
        s"<p class=govuk-body><span><a id='breakdown' class=govuk-link href=${
          controllers.financialOverview.routes.PaymentsAndChargesInterestController
            .onPageLoad(srn, pstr, schemeFS.periodStartDate, "1", AccountingForTaxCharges, Some(versionInt), Some(submittedDate), Overdue).url
        }>" +
        s" ${messages("paymentsAndCharges.chargeDetails.interest.paid")}</a></span></p>"

    )
  }

  private def insetTextForInterestWithQuarter(schemeFS: SchemeFS): uk.gov.hmrc.viewmodels.Html = {
    uk.gov.hmrc.viewmodels.Html(
      s"<p class=govuk-body>${messages("financialPaymentsAndCharges.interest.chargeReference.text2", schemeFS.chargeType.toString.toLowerCase())}</p>" +
        s"<p class=govuk-body><a id='breakdown' class=govuk-link href=${controllers.financialOverview.routes.PaymentsAndChargeDetailsController
          .onPageLoad(srn, pstr, schemeFS.periodStartDate, "1", AccountingForTaxCharges, Some(versionInt), Some(submittedDate), Overdue).url}>" +
        s"${messages("financialPaymentsAndCharges.interest.chargeReference.linkText")}</a></p>"
    )
  }

  private def expectedJson(
                            schemeFS: SchemeFS,
                            insetText: uk.gov.hmrc.viewmodels.Html,
                            isPaymentOverdue: Boolean = false,
                            optHint: Option[String] = None
                          ): JsObject = {
    val commonJson = Json.obj(
      "chargeDetailsList" -> Nil,
      "tableHeader" -> "",
      "schemeName" -> schemeName,
      "chargeType" -> (schemeFS.chargeType.toString + s" submission $version"),
      "versionValue" -> (s" submission $version"),
      "isPaymentOverdue" -> isPaymentOverdue,
      "insetText" -> insetText,
      "interest" -> schemeFS.accruedInterestTotal,
      "returnLinkBasedOnJourney" -> "your overdue payments and charges",
      "returnUrl" -> "/manage-pension-scheme-accounting-for-tax/test-srn/financial-overview/pstr/overdue-payments-and-charges",
      "returnHistoryURL" -> "/manage-pension-scheme-accounting-for-tax/test-srn/2020-04-01/submission/1/summary"
    )
    optHint match {
      case Some(_) => commonJson ++ Json.obj("hintText" -> messages("paymentsAndCharges.interest.hint"))
      case _ => commonJson
    }
  }

  "PaymentsAndChargesController" must {

    "return OK and the correct view with inset text linked to interest page if amount is due and interest is accruing for a GET" in {
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(Seq(
              createChargeWithAmountDueAndInterest("XY002610150183", amountDue = 1234.00),
              createChargeWithAmountDueAndInterest("XY002610150184", amountDue = 1234.00)
            ))
        ))

      val schemeFS = createChargeWithAmountDueAndInterest(chargeReference = "XY002610150184", amountDue = 1234.00)
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      val result = route(application, httpGETRequest(httpPathGET(index = "1"))).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1))
        .render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual "financialOverview/paymentsAndChargeDetails.njk"

      jsonCaptor.getValue must containJson(
        expectedJson(schemeFS, insetTextWithAmountDueAndInterest(schemeFS), isPaymentOverdue = true)
      )
    }

    "return OK and the correct view with hint text linked to interest page if amount is due and interest is not accruing for a GET" in {
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(Seq(
              createChargeWithAmountDueAndInterestPayment("XY002610150188", interest = BigDecimal(0.00)),
              createChargeWithAmountDueAndInterestPayment("XY002610150189", interest = BigDecimal(0.00))
            )
          )
        ))

      val schemeFS = createChargeWithAmountDueAndInterestPayment(
        chargeReference = "XY002610150188",
        interest = BigDecimal(0.00)
      )
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      val result = route(application, httpGETRequest(httpPathGET(index = "0"))).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1))
        .render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual "financialOverview/paymentsAndChargeDetails.njk"
      jsonCaptor.getValue must containJson(
        expectedJson(schemeFS, Html(""), optHint = Some(messages("paymentsAndCharges.interest.hint")))
      )
    }

    "return OK and the correct view with inset text linked to original charge page if linked interest is present and Quarter is applicable for a GET" in {
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(Seq(
          createChargeWithAmountDueAndInterest("XY002610150183", amountDue = 1234.00),
          createChargeWithSourceChargeReference("XY002610150184", "XY002610150183", amountDue = 123.00)
        ))
        ))
      when(mockPaymentsAndChargesService.chargeRefs(any())).thenReturn(mapAFTResponse)

      val schemeFS = createChargeWithSourceChargeReference("XY002610150184", "XY002610150183", amountDue = 123.00)
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      val result = route(application, httpGETRequest(httpPathGET(index = "1"))).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1))
        .render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual "financialOverview/paymentsAndChargeDetails.njk"

      jsonCaptor.getValue must containJson(
        expectedJson(schemeFS, insetTextForInterestWithQuarter(schemeFS))
      )
    }


    "return OK and the correct view with no inset text if amount is all paid and no interest accrued for a GET" in {
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(Seq(createChargeWithAmountDueAndInterest("XY002610150187", interest = 0.00))
          )
        ))
      val schemeFS = createChargeWithAmountDueAndInterest(chargeReference = "XY002610150187", interest = 0.00)
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      val result = route(application, httpGETRequest(httpPathGET(index = "0"))).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1))
        .render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual "financialOverview/paymentsAndChargeDetails.njk"
      jsonCaptor.getValue must containJson(expectedJson(schemeFS, uk.gov.hmrc.viewmodels.Html("")))
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
  }
}

object PaymentsAndChargeDetailsControllerSpec {
  private val srn = "test-srn"

  def psaFS(chargeReference: String): PsaFS =
    PsaFS(
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
      pstr = "24000040IN"
    )

  private def createChargeWithAmountDueAndInterest(
                                                    chargeReference: String,
                                                    chargeType: SchemeFSChargeType = PSS_AFT_RETURN,
                                                    amountDue: BigDecimal = 0.00,
                                                    interest: BigDecimal = 123.00
                                                  ): SchemeFS = {
    SchemeFS(
      chargeReference = chargeReference,
      chargeType = chargeType,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 56432.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = amountDue,
      accruedInterestTotal = interest,
      periodStartDate = LocalDate.parse(QUARTER_START_DATE),
      periodEndDate = LocalDate.parse(QUARTER_END_DATE),
      formBundleNumber = None,
      sourceChargeRefForInterest = None,
      documentLineItemDetails = Nil
    )
  }
  private def createChargeWithSourceChargeReference(
                                                    chargeReference: String,
                                                    sourceChargeReference: String,
                                                    chargeType: SchemeFSChargeType = PSS_AFT_RETURN_INTEREST,
                                                    amountDue: BigDecimal = 0.00,
                                                    interest: BigDecimal = 123.00
                                                  ): SchemeFS = {
    SchemeFS(
      chargeReference = chargeReference,
      chargeType = chargeType,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 56432.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = amountDue,
      accruedInterestTotal = interest,
      periodStartDate = LocalDate.parse(QUARTER_START_DATE),
      periodEndDate = LocalDate.parse(QUARTER_END_DATE),
      formBundleNumber = None,
      sourceChargeRefForInterest = Some(sourceChargeReference),
      documentLineItemDetails = Nil
    )
  }

  private def createChargeWithAmountDueAndInterestPayment(
                                                           chargeReference: String,
                                                           amountDue: BigDecimal = 0.00,
                                                           interest: BigDecimal
                                                         ): SchemeFS = {
    SchemeFS(
      chargeReference = chargeReference,
      chargeType = PSS_AFT_RETURN_INTEREST,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 56432.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = amountDue,
      accruedInterestTotal = interest,
      periodStartDate = LocalDate.parse(QUARTER_START_DATE),
      periodEndDate = LocalDate.parse(QUARTER_END_DATE),
      formBundleNumber = None,
      sourceChargeRefForInterest = None,
      documentLineItemDetails = Nil
    )
  }

  private def createChargeWithDeltaCredit(): SchemeFS = {
    SchemeFS(
      chargeReference = "XY002610150185",
      chargeType = PSS_AFT_RETURN,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = -20000.00,
      outstandingAmount = 0.00,
      stoodOverAmount = 0.00,
      amountDue = 0.00,
      accruedInterestTotal = 0.00,
      periodStartDate = LocalDate.parse(QUARTER_START_DATE),
      periodEndDate = LocalDate.parse(QUARTER_END_DATE),
      formBundleNumber = None,
      sourceChargeRefForInterest = None,
      documentLineItemDetails = Nil
    )
  }

  private val schemeFSResponse: Seq[SchemeFS] = Seq(
    createChargeWithDeltaCredit(),
    createChargeWithAmountDueAndInterest(chargeReference = "XY002610150186"),
    createChargeWithAmountDueAndInterest(chargeReference = "XY002610150184", amountDue = 1234.00),
    createChargeWithAmountDueAndInterest(chargeReference = "XY002610150187", interest = 0.00),
    createChargeWithAmountDueAndInterestPayment(chargeReference = "XY002610150188", interest = 0.00)
  )

  private val mapAFTResponse: Map[(String,String), Seq[String]] = {
    Map((AccountingForTaxCharges.toString, QUARTER_START_DATE.toString) -> Seq("XY002610150184", "XY002610150183"))
  }

}
