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
import forms.financialStatement.PaymentOrChargeTypeFormProvider
import matchers.JsonMatchers
import models.ChargeDetailsFilter.All
import models.financialStatement.PaymentOrChargeType.AccountingForTaxCharges
import models.financialStatement.{DisplayPaymentOrChargeType, PaymentOrChargeType, SchemeFSDetail}
import models.requests.IdentifierRequest
import models.{Enumerable, PaymentOverdue}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.mvc.Results
import play.api.test.Helpers._
import services.financialOverview.scheme.{PaymentsAndChargesService, PaymentsCache}
import views.html.financialOverview.scheme.PaymentOrChargeTypeView

import scala.concurrent.Future

class PaymentOrChargeTypeControllerSpec extends ControllerSpecBase with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  implicit val config: FrontendAppConfig = mockAppConfig
  val mockPaymentsAndChargesService: PaymentsAndChargesService = mock[PaymentsAndChargesService]
  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[PaymentsAndChargesService].toInstance(mockPaymentsAndChargesService)
  )

  private val displayPaymentOrChargeType: Seq[DisplayPaymentOrChargeType] = Seq(
    DisplayPaymentOrChargeType(AccountingForTaxCharges, Some(PaymentOverdue))
  )

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()
  val formProvider = new PaymentOrChargeTypeFormProvider()
  val form: Form[PaymentOrChargeType] = formProvider()

  lazy val httpPathGET: String = routes.PaymentOrChargeTypeController.onPageLoad(srn, All).url
  lazy val httpPathPOST: String = routes.PaymentOrChargeTypeController.onSubmit(srn, All).url

  private val paymentsCache: Seq[SchemeFSDetail] => PaymentsCache = schemeFSDetail => PaymentsCache(psaId, srn, schemeDetails, schemeFSDetail)

  private val valuesValid: Map[String, Seq[String]] = Map("value" -> Seq(AccountingForTaxCharges.toString))
  private val valuesInvalid: Map[String, Seq[String]] = Map("value" -> Seq("false"))

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[?])).thenReturn(dummyCall.url)
    when(mockPaymentsAndChargesService.isPaymentOverdue).thenReturn(_ => true)
    when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(),
      any(), any())(any(), any())).thenReturn(Future.successful(paymentsCache(schemeFSResponseAftAndOTC.seqSchemeFSDetail)))
  }

  "PaymentOrChargeType Controller" must {
    "return OK and the correct view for a GET" in {

      val req = httpGETRequest(httpPathGET)
      val result = route(application, req).value

      status(result) mustEqual OK

      val view = application.injector.instanceOf[PaymentOrChargeTypeView].apply(
        form = form,
        title = messages(s"paymentOrChargeType.all.title"),
        schemeName = schemeName,
        submitCall = routes.PaymentOrChargeTypeController.onSubmit(srn, All),
        returnUrl = "/financial-overview/aa",
        returnDashboardUrl = Option(mockAppConfig.managePensionsSchemeSummaryUrl).getOrElse("/pension-scheme-summary/%s").format(srn),
        radios = PaymentOrChargeType.radios(form, displayPaymentOrChargeType,
        Seq("govuk-tag govuk-tag--red govuk-!-display-inline"), areLabelsBold = false),
        journeyType = All
      )(req, messages)

      compareResultAndView(result, view)

    }

    "redirect to next page when valid data is submitted" in {

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result) mustBe Some(routes.AllPaymentsAndChargesController.onPageLoad(srn, startDate.toString, AccountingForTaxCharges).url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
    }
  }
}

