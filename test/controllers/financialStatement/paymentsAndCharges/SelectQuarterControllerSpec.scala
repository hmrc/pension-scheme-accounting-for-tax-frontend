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
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.QuartersFormProvider
import matchers.JsonMatchers
import models.ChargeDetailsFilter.All
import models.financialStatement.PaymentOrChargeType.AccountingForTaxCharges
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
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import services.paymentsAndCharges.PaymentsAndChargesService
import utils.TwirlMigration
import views.html.financialStatement.paymentsAndCharges.SelectQuarterView

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
  val templateToBeRendered = "financialStatement/paymentsAndCharges/selectQuarter.njk"
  val formProvider = new QuartersFormProvider()
  val form: Form[AFTQuarter] = formProvider("selectChargesQuarter.error", quarters)

  lazy val httpPathGET: String = routes.SelectQuarterController.onPageLoad(srn, year, All).url
  lazy val httpPathPOST: String = routes.SelectQuarterController.onSubmit(srn, year, All).url

  private val valuesValid: Map[String, Seq[String]] = Map("value" -> Seq(q22020.toString))
  private val valuesInvalid: Map[String, Seq[String]] = Map("year" -> Seq("20"))

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockPaymentsAndChargesService.isPaymentOverdue).thenReturn(_ => true)
    when(mockPaymentsAndChargesService.getPaymentsForJourney(any(),
      any(), any())(any(), any())).thenReturn(Future.successful(paymentsCache(schemeFSResponseAftAndOTC.seqSchemeFSDetail)))
  }

  "SelectQuarter Controller" must {
    "return OK and the correct view for a GET" in {
      val result = route(application, httpGETRequest(httpPathGET)).value

      val view = application.injector.instanceOf[SelectQuarterView].apply(
        form,
        "Which quarter of 2020 do you want to view the Accounting for Tax charges for?",
        year,
        TwirlMigration.toTwirlRadiosWithHintText(
          Quarters.radios(form, displayQuarters, Seq("govuk-tag govuk-tag--red govuk-!-display-inline"), areLabelsBold = false)),
        routes.SelectQuarterController.onSubmit(srn, year, All),
        returnUrl = dummyCall.url,
        schemeName
      )(httpGETRequest(httpPathGET), messages)

      status(result) mustEqual OK
      compareResultAndView(result, view)
    }

    "redirect to next page when valid data is submitted" in {

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result) mustBe Some(routes.PaymentsAndChargesController.onPageLoad(srn, q22020.startDate.toString, AccountingForTaxCharges, All).url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
    }
  }
}

