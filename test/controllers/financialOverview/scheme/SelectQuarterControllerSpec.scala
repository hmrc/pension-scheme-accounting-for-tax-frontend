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
import data.SampleData._
import forms.QuartersFormProvider
import matchers.JsonMatchers
import models.financialStatement.PaymentOrChargeType.AccountingForTaxCharges
import models.financialStatement.SchemeFSDetail
import models.requests.IdentifierRequest
import models.{AFTQuarter, DisplayQuarter, Enumerable, PaymentOverdue, Quarters}
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
import views.html.financialOverview.scheme.SelectQuarterView

import scala.concurrent.Future

class SelectQuarterControllerSpec extends ControllerSpecBase with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  implicit val config: FrontendAppConfig = mockAppConfig
  val mockPaymentsAndChargesService: PaymentsAndChargesService = mock[PaymentsAndChargesService]
  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[PaymentsAndChargesService].toInstance(mockPaymentsAndChargesService)
  )

  private val year = "2020"

  private val quarters: Seq[AFTQuarter] = Seq(q22020)
  private val displayQuarters: Seq[DisplayQuarter] = Seq(
    DisplayQuarter(q22020, displayYear = false, None, Some(PaymentOverdue))
  )

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()
  val formProvider = new QuartersFormProvider()
  val form: Form[AFTQuarter] = formProvider("selectChargesQuarter.error", quarters)

  lazy val httpPathGET: String = routes.SelectQuarterController.onPageLoad(srn, year).url
  lazy val httpPathPOST: String = routes.SelectQuarterController.onSubmit(srn, year).url
  private val paymentsCache: Seq[SchemeFSDetail] => PaymentsCache = schemeFSDetail => PaymentsCache(psaId, srn, schemeDetails, schemeFSDetail)
  private val submitCall: Call = routes.SelectQuarterController.onSubmit(srn, year)

  private val valuesValid: Map[String, Seq[String]] = Map("value" -> Seq(q22020.toString))
  private val valuesInvalid: Map[String, Seq[String]] = Map("year" -> Seq("20"))

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[?])).thenReturn(dummyCall.url)
    when(mockPaymentsAndChargesService.isPaymentOverdue).thenReturn(_ => true)
    when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(paymentsCache(schemeFSResponseAftAndOTC.seqSchemeFSDetail)))
  }

  "SelectQuarter Controller" must {
    "return OK and the correct view for a GET" in {

      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(schemeFSResponseAftAndOTC.seqSchemeFSDetail)))

      val request = httpGETRequest(httpPathGET)
      val result = route(application, request).value

      status(result) mustEqual OK

      val view = application.injector.instanceOf[SelectQuarterView].apply(
        form = form,
        submitCall = submitCall,
        schemeName = schemeName,
        returnUrl = s"/financial-overview/aa",
        returnDashboardUrl = Option(mockAppConfig.managePensionsSchemeSummaryUrl).getOrElse("/pension-scheme-summary/%s").format(srn),
        radios = Quarters.radios(
            form,
            displayQuarters,
            Seq("govuk-tag govuk-tag--red govuk-!-display-inline")),
        year
      )(request, messages)

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "redirect to next page when valid data is submitted" in {

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result) mustBe Some(routes.AllPaymentsAndChargesController.onPageLoad(srn, q22020.startDate.toString, AccountingForTaxCharges).url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
    }
  }
}

