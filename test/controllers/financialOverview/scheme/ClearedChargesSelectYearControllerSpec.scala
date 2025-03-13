/*
 * Copyright 2025 HM Revenue & Customs
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
import data.SampleData.{dummyCall, psaId, schemeDetails, schemeFSResponseAftAndOTC, schemeName, srn}
import forms.YearsFormProvider
import models.{Enumerable, Year}
import models.financialStatement.PaymentOrChargeType
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.Application
import play.api.data.Form
import play.api.http.Status.OK
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import services.financialOverview.scheme.{PaymentsAndChargesService, PaymentsCache}
import play.api.inject.bind
import play.api.test.Helpers.{route, _}
import views.html.financialOverview.scheme.ClearedChargesSelectYearView
import models.{DisplayYear, FSYears}
import models.requests.IdentifierRequest
import play.api.i18n.Messages

import scala.concurrent.Future

class ClearedChargesSelectYearControllerSpec extends ControllerSpecBase {
  private def httpPathGET: String =
    routes.ClearedChargesSelectYearController.onPageLoad(srn, PaymentOrChargeType.AccountingForTaxCharges).url

  private def httpPathPOST: String =
    routes.ClearedChargesSelectYearController.onSubmit(srn, PaymentOrChargeType.AccountingForTaxCharges).url

  private def redirectUrl = routes.ClearedPaymentsAndChargesController.onPageLoad(srn, "2021", PaymentOrChargeType.AccountingForTaxCharges).url

  private val mockPaymentsAndChargesService = mock[PaymentsAndChargesService]

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

  private val schemeFSDetails = schemeFSResponseAftAndOTC.seqSchemeFSDetail
  private val samplePaymentsCache = PaymentsCache(psaId, srn, schemeDetails, schemeFSDetails)
  val formProvider = new YearsFormProvider()
  private val years = Seq(DisplayYear(2021, None))
  implicit val ev: Enumerable[Year] = FSYears.enumerable(years.map(_.year))
  val form: Form[Year] = formProvider()

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    reset(mockPaymentsAndChargesService)
    when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(samplePaymentsCache))
    when(mockPaymentsAndChargesService.getTypeParam(any())(any())).thenReturn("accounting-for-tax")
  }

  private val radios = FSYears.radios(form, years, isYearRangeFormat = true)

  private val valuesValid: Map[String, Seq[String]] = Map("value" -> Seq("2021"))
  private val valuesInvalid: Map[String, Seq[String]] = Map("year" -> Seq("20"))

  "ClearedChargesSelectYearController" must {
    "return OK and the correct view" in {
      val result = route(application, httpGETRequest(httpPathGET)).value
      status(result) mustEqual OK

      val view = application.injector.instanceOf[ClearedChargesSelectYearView].apply(
        form,
        Messages("schemeFinancial.clearedPaymentsAndCharges"),
        routes.ClearedChargesSelectYearController.onSubmit(srn, PaymentOrChargeType.AccountingForTaxCharges),
        schemeName,
        s"/financial-overview/aa",
        returnDashboardUrl = Option(mockAppConfig.managePensionsSchemeSummaryUrl).getOrElse("/pension-scheme-summary/%s").format(srn),
        radios
      )(httpGETRequest(httpPathGET), messages)

      compareResultAndView(result, view)
    }

    "redirect to the correct page when valid data is submitted" in {

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual redirectUrl
    }

    "return a BAD REQUEST when invalid data is submitted" in {

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
    }
  }

}
