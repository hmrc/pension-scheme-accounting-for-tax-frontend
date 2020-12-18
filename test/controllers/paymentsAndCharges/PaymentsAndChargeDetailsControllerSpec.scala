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

import config.FrontendAppConfig
import connectors.FinancialStatementConnector
import controllers.actions.{FakeIdentifierAction, IdentifierAction}
import controllers.base.ControllerSpecBase
import data.SampleData._
import helpers.FormatHelper
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.financialStatement.PsaFSChargeType.AFT_INITIAL_LFP
import models.financialStatement.SchemeFSChargeType.{PSS_AFT_RETURN, PSS_AFT_RETURN_INTEREST}
import models.financialStatement.{PsaFS, SchemeFS}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest._
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.test.Helpers.{route, _}
import services.SchemeService
import services.paymentsAndCharges.PaymentsAndChargesService
import uk.gov.hmrc.nunjucks.NunjucksRenderer
import uk.gov.hmrc.viewmodels.{Html, NunjucksSupport}
import utils.AFTConstants._
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}

import java.time.LocalDate
import scala.concurrent.Future

class PaymentsAndChargeDetailsControllerSpec
  extends ControllerSpecBase
    with NunjucksSupport
    with JsonMatchers
    with BeforeAndAfterEach
    with RecoverMethods {

  import PaymentsAndChargeDetailsControllerSpec._

  private def httpPathGET(startDate: LocalDate = QUARTER_START_DATE, index: String): String =
    controllers.paymentsAndCharges.routes.PaymentsAndChargeDetailsController.onPageLoad(srn, startDate, index).url

  private val mockSchemeService: SchemeService = mock[SchemeService]
  private val mockFSConnector: FinancialStatementConnector = mock[FinancialStatementConnector]
  private val mockPaymentsAndChargesService: PaymentsAndChargesService = mock[PaymentsAndChargesService]
  private val application: Application = new GuiceApplicationBuilder()
    .overrides(
      Seq[GuiceableModule](
        bind[IdentifierAction].to[FakeIdentifierAction],
        bind[NunjucksRenderer].toInstance(mockRenderer),
        bind[FrontendAppConfig].toInstance(mockAppConfig),
        bind[SchemeService].toInstance(mockSchemeService),
        bind[FinancialStatementConnector].toInstance(mockFSConnector),
        bind[PaymentsAndChargesService].toInstance(mockPaymentsAndChargesService)
      ): _*
    )
    .build()

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockSchemeService, mockFSConnector, mockRenderer, mockPaymentsAndChargesService)
    when(mockAppConfig.managePensionsSchemeSummaryUrl)
      .thenReturn(dummyCall.url)
    when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(schemeDetails))
    when(mockFSConnector.getSchemeFS(any())(any(), any()))
      .thenReturn(Future.successful(schemeFSResponse))
    when(mockPaymentsAndChargesService.getChargeDetailsForSelectedCharge(any())(any()))
      .thenReturn(Nil)
    when(mockRenderer.render(any(), any())(any()))
      .thenReturn(Future.successful(play.twirl.api.Html("")))
  }

  private def insetTextWithAmountDueAndInterest(schemeFS: SchemeFS, index: String): uk.gov.hmrc.viewmodels.Html = {
    uk.gov.hmrc.viewmodels.Html(
      s"<h2 class=govuk-heading-s>${messages("paymentsAndCharges.chargeDetails.interestAccruing")}</h2>" +
        s"<p class=govuk-body>${
          messages("paymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate",
            schemeFS.dueDate.getOrElse(LocalDate.now()).format(dateFormatterDMY))
        }" +
        s" <span>" +
        s"<a id='breakdown' class=govuk-link href=${
          controllers.paymentsAndCharges.routes.PaymentsAndChargesInterestController
            .onPageLoad(srn, schemeFS.periodStartDate, index)
            .url
        }>" +
        s"${messages("paymentsAndCharges.chargeDetails.interest.paid")}</a></span></p>"
    )
  }

  private def insetTextWithNoAmountDue(schemeFS: SchemeFS): uk.gov.hmrc.viewmodels.Html = {
    uk.gov.hmrc.viewmodels.Html(
      s"<h2 class=govuk-heading-s>${messages("paymentsAndCharges.chargeDetails.interestAccrued")}</h2>" +
        s"<p class=govuk-body>${
          messages("paymentsAndCharges.chargeDetails.amount.paid.after.dueDate",
            schemeFS.dueDate.getOrElse(LocalDate.now()).format(dateFormatterDMY))
        }</p>"
    )
  }

  private def expectedJson(
                            schemeFS: SchemeFS,
                            insetText: uk.gov.hmrc.viewmodels.Html,
                            isPaymentOverdue: Boolean = false,
                            isInCredit: Boolean = false,
                            optHint: Option[String] = None
                          ): JsObject = {
    val commonJson = Json.obj(
      "chargeDetailsList" -> Nil,
      "tableHeader" -> messages("paymentsAndCharges.caption",
        schemeFS.periodStartDate.format(dateFormatterStartDate),
        schemeFS.periodEndDate.format(dateFormatterDMY)),
      "schemeName" -> schemeName,
      "chargeType" -> schemeFS.chargeType.toString,
      "chargeReferenceTextMessage" -> (if (isInCredit) {
        messages(
          "paymentsAndCharges.credit.information",
          s"${FormatHelper.formatCurrencyAmountAsString(schemeFS.totalAmount.abs)}"
        )
      }
      else {
        messages("paymentsAndCharges.chargeDetails.chargeReference", schemeFS.chargeReference)
      }),
      "isPaymentOverdue" -> isPaymentOverdue,
      "insetText" -> insetText,
      "interest" -> schemeFS.accruedInterestTotal,
      "returnUrl" -> dummyCall.url,
      "returnHistoryURL" -> controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, startDate).url
    )
    optHint match {
      case Some(_) => commonJson ++ Json.obj("hintText" -> messages("paymentsAndCharges.interest.hint"))
      case _ => commonJson
    }
  }

  "PaymentsAndChargesController" must {

    "return OK and the correct view with inset text linked to interest page if amount is due and interest is accruing for a GET" in {
      when(mockPaymentsAndChargesService.groupAndSortByStartDate(any(), any()))
        .thenReturn(Seq(
          (
            LocalDate.parse(QUARTER_START_DATE),
            Seq(
              createChargeWithAmountDueAndInterest("XY002610150183", amountDue = 1234.00),
              createChargeWithAmountDueAndInterest("XY002610150184", amountDue = 1234.00)
            )
          )
        ))

      val schemeFS = createChargeWithAmountDueAndInterest(chargeReference = "XY002610150184", amountDue = 1234.00)
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      val result = route(application, httpGETRequest(httpPathGET(index = "1"))).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1))
        .render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual "paymentsAndCharges/paymentsAndChargeDetails.njk"

      jsonCaptor.getValue must containJson(
        expectedJson(schemeFS, insetTextWithAmountDueAndInterest(schemeFS, "1"), isPaymentOverdue = true)
      )
    }

    "return OK and the correct view with hint text linked to interest page if amount is due and interest is not accruing for a GET" in {
      when(mockPaymentsAndChargesService.groupAndSortByStartDate(any(), any()))
        .thenReturn(Seq(
          (
            LocalDate.parse(QUARTER_START_DATE),
            Seq(
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

      templateCaptor.getValue mustEqual "paymentsAndCharges/paymentsAndChargeDetails.njk"
      jsonCaptor.getValue must containJson(
        expectedJson(schemeFS, Html(""), optHint = Some(messages("paymentsAndCharges.interest.hint")))
      )
    }

    "return OK and the correct view with inset text if amount is all paid and interest accrued has been created as another charge for a GET" in {
      when(mockPaymentsAndChargesService.groupAndSortByStartDate(any(), any()))
        .thenReturn(Seq(
          (
            LocalDate.parse(QUARTER_START_DATE),
            Seq(createChargeWithAmountDueAndInterest("XY002610150186"))
          )
        ))
      val schemeFS = createChargeWithAmountDueAndInterest(chargeReference = "XY002610150186")
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      val result = route(application, httpGETRequest(httpPathGET(index = "0"))).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1))
        .render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual "paymentsAndCharges/paymentsAndChargeDetails.njk"
      jsonCaptor.getValue must containJson(expectedJson(schemeFS, insetTextWithNoAmountDue(schemeFS)))
    }

    "return OK and the correct view with no inset text if amount is all paid and no interest accrued for a GET" in {
      when(mockPaymentsAndChargesService.groupAndSortByStartDate(any(), any()))
        .thenReturn(Seq(
          (
            LocalDate.parse(QUARTER_START_DATE),
            Seq(createChargeWithAmountDueAndInterest("XY002610150187", interest = 0.00))
          )
        ))
      val schemeFS = createChargeWithAmountDueAndInterest(chargeReference = "XY002610150187", interest = 0.00)
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      val result = route(application, httpGETRequest(httpPathGET(index = "0"))).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1))
        .render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual "paymentsAndCharges/paymentsAndChargeDetails.njk"
      jsonCaptor.getValue must containJson(expectedJson(schemeFS, uk.gov.hmrc.viewmodels.Html("")))
    }

    "return OK and the correct view with no inset text and correct chargeReference text if amount is in credit for a GET" in {
      when(mockPaymentsAndChargesService.groupAndSortByStartDate(any(), any()))
        .thenReturn(Seq(
          (
            LocalDate.parse(QUARTER_START_DATE),
            Seq(createChargeWithDeltaCredit("XY002610150185"))
          )
        ))
      val schemeFS = createChargeWithDeltaCredit(chargeReference = "XY002610150185")
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      val result = route(application, httpGETRequest(httpPathGET(index = "0"))).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1))
        .render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual "paymentsAndCharges/paymentsAndChargeDetails.njk"
      jsonCaptor.getValue must containJson(expectedJson(schemeFS, uk.gov.hmrc.viewmodels.Html(""), isInCredit = true))
    }

    "catch IndexOutOfBoundsException" in {
      when(mockPaymentsAndChargesService.groupAndSortByStartDate(any(), any()))
        .thenReturn(Seq(
          (
            LocalDate.parse(QUARTER_START_DATE),
            Seq(createChargeWithAmountDueAndInterest("XY002610150185"))
          )
        ))

      val result = route(application, httpGETRequest(httpPathGET(index = "1"))).value

      status(result) mustBe SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
    }

    "return charge details for XY002610150184 for startDate 2020-04-01 index 1" in {
      when(mockPaymentsAndChargesService.groupAndSortByStartDate(any(), any()))
        .thenReturn(Seq(
          (
            LocalDate.parse("2020-01-01"),
            Seq(
              createChargeWithAmountDueAndInterest("XY002610150181", amountDue = 1234.00),
              createChargeWithAmountDueAndInterest("XY002610150182", amountDue = 1234.00)
            )
          ),
          (
            LocalDate.parse("2020-04-01"),
            Seq(
              createChargeWithAmountDueAndInterest("XY002610150183", amountDue = 1234.00),
              createChargeWithAmountDueAndInterest("XY002610150184", amountDue = 1234.00)
            )
          )
        ))

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      val result = route(application, httpGETRequest(httpPathGET("2020-04-01", index = "1"))).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1))
        .render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual "paymentsAndCharges/paymentsAndChargeDetails.njk"

      jsonCaptor.getValue.value("chargeReferenceTextMessage") mustBe JsString("Charge reference: XY002610150184")
    }

    "return charge details for XY002610150181 for startDate 2020-01-01 index 0" in {
      when(mockPaymentsAndChargesService.groupAndSortByStartDate(any(), any()))
        .thenReturn(Seq(
          (
            LocalDate.parse("2020-01-01"),
            Seq(
              createChargeWithAmountDueAndInterest("XY002610150181", amountDue = 1234.00),
              createChargeWithAmountDueAndInterest("XY002610150182", amountDue = 1234.00)
            )
          ),
          (
            LocalDate.parse("2020-04-01"),
            Seq(
              createChargeWithAmountDueAndInterest("XY002610150183", amountDue = 1234.00),
              createChargeWithAmountDueAndInterest("XY002610150184", amountDue = 1234.00)
            )
          )
        ))

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      val result = route(application, httpGETRequest(httpPathGET("2020-01-01", index = "0"))).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1))
        .render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual "paymentsAndCharges/paymentsAndChargeDetails.njk"

      jsonCaptor.getValue.value("chargeReferenceTextMessage") mustBe JsString("Charge reference: XY002610150181")
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
      amountDue = 1029.05,
      periodStartDate = LocalDate.parse("2020-04-01"),
      periodEndDate = LocalDate.parse("2020-06-30"),
      pstr = "24000040IN"
    )

  private def createChargeWithAmountDueAndInterest(
                                                    chargeReference: String,
                                                    amountDue: BigDecimal = 0.00,
                                                    interest: BigDecimal = 123.00
                                                  ): SchemeFS = {
    SchemeFS(
      chargeReference = chargeReference,
      chargeType = PSS_AFT_RETURN,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 56432.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = amountDue,
      accruedInterestTotal = interest,
      periodStartDate = LocalDate.parse(QUARTER_START_DATE),
      periodEndDate = LocalDate.parse(QUARTER_END_DATE)
    )
  }

  private def createChargeWithAmountDueAndInterestPayment(
                                                           chargeReference: String,
                                                           amountDue: BigDecimal = 0.00,
                                                           interest: BigDecimal = 123.00
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
      periodEndDate = LocalDate.parse(QUARTER_END_DATE)
    )
  }

  private def createChargeWithDeltaCredit(chargeReference: String): SchemeFS = {
    SchemeFS(
      chargeReference = chargeReference,
      chargeType = PSS_AFT_RETURN,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = -20000.00,
      outstandingAmount = 0.00,
      stoodOverAmount = 0.00,
      amountDue = 0.00,
      accruedInterestTotal = 0.00,
      periodStartDate = LocalDate.parse(QUARTER_START_DATE),
      periodEndDate = LocalDate.parse(QUARTER_END_DATE)
    )
  }

  private val schemeFSResponse: Seq[SchemeFS] = Seq(
    createChargeWithDeltaCredit(chargeReference = "XY002610150185"),
    createChargeWithAmountDueAndInterest(chargeReference = "XY002610150186"),
    createChargeWithAmountDueAndInterest(chargeReference = "XY002610150184", amountDue = 1234.00),
    createChargeWithAmountDueAndInterest(chargeReference = "XY002610150187", interest = 0.00),
    createChargeWithAmountDueAndInterestPayment(chargeReference = "XY002610150188", interest = 0.00)
  )
}
