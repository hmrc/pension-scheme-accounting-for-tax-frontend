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
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData._
import forms.YearsFormProvider
import matchers.JsonMatchers
import models.StartYears.enumerable
import models.financialStatement.PaymentOrChargeType.AccountingForTaxCharges
import models.financialStatement.SchemeFSDetail
import models.requests.IdentifierRequest
import models.{DisplayYear, Enumerable, FSYears, PaymentOverdue, Year}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.Json
import play.api.mvc.{Call, Results}
import play.api.test.Helpers._
import services.financialOverview.scheme.{PaymentsAndChargesService, PaymentsCache}
import utils.AFTConstants.QUARTER_START_DATE
import views.html.financialOverview.scheme.SelectYearView

import java.time.LocalDate
import scala.concurrent.Future

class SelectYearControllerSpec extends ControllerSpecBase with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  implicit val config: FrontendAppConfig = mockAppConfig
  val mockPaymentsAndChargesService: PaymentsAndChargesService = mock[PaymentsAndChargesService]
  val mockSelectYearController: SelectYearController = mock[SelectYearController]

  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[PaymentsAndChargesService].toInstance(mockPaymentsAndChargesService)
  )

  private val paymentsCache: Seq[SchemeFSDetail] => PaymentsCache = schemeFSDetail => PaymentsCache(psaId, srn, schemeDetails, schemeFSDetail)

  private val years: Seq[DisplayYear] = Seq(DisplayYear(2020, Some(PaymentOverdue)))

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()
  val formProvider = new YearsFormProvider()
  val form: Form[Year] = formProvider()

  lazy val httpPathGET: String = routes.SelectYearController.onPageLoad(srn, AccountingForTaxCharges).url
  private val submitCall: Call = routes.SelectYearController.onSubmit(srn, AccountingForTaxCharges)
  lazy val httpPathPOST: String = submitCall.url

  private val year = "2020"

  private val valuesValid: Map[String, Seq[String]] = Map("value" -> Seq(year))
  private val valuesInvalid: Map[String, Seq[String]] = Map("year" -> Seq("20"))

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockPaymentsAndChargesService.isPaymentOverdue).thenReturn(_ => true)
    when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(paymentsCache(schemeFSResponseAftAndOTC.seqSchemeFSDetail)))
    when(mockPaymentsAndChargesService.getTypeParam(any())(any())).thenReturn("event reporting")
  }

  "SelectYear Controller" must {
    "return OK and the correct view for a GET" in {

      val typeParam = mockPaymentsAndChargesService.getTypeParam(AccountingForTaxCharges)

      val request = httpGETRequest(httpPathGET)
      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      val view = application.injector.instanceOf[SelectYearView].apply(
        form = form,
        titleMessage = "Which year do you want to view event reporting for?",
        penaltyType = typeParam,
        submitCall = submitCall,
        schemeName = SampleData.schemeName,
        returnUrl = "/financial-overview/aa",
        returnDashboardUrl = Option(mockAppConfig.managePensionsSchemeSummaryUrl).getOrElse("/pension-scheme-summary/%s").format(srn),
        radios = FSYears.radios(form, years)
      )(request, messages)

      compareResultAndView(result, view)
    }

    "redirect to next page when valid data is submitted and a single quarter is found for the selected year" in {

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result) mustBe Some(routes.AllPaymentsAndChargesController
        .onPageLoad(srn, QUARTER_START_DATE.toString, AccountingForTaxCharges).url)
    }

    "redirect to next page when valid data is submitted and multiple quarters are found for the selected year" in {
      val schemeFSDetail = schemeFSResponseAftAndOTC.seqSchemeFSDetail.head.copy(periodStartDate = Some(LocalDate.parse("2020-07-01")))
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(schemeFSResponseAftAndOTC.seqSchemeFSDetail :+ schemeFSDetail)))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result) mustBe Some(routes.SelectQuarterController.onPageLoad(srn, year).url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
    }
  }
}

