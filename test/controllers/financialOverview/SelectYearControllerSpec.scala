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

import config.FrontendAppConfig
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.YearsFormProvider
import matchers.JsonMatchers
import models.StartYears.enumerable
import models.financialStatement.PaymentOrChargeType.AccountingForTaxCharges
import models.financialStatement.SchemeFS
import models.requests.IdentifierRequest
import models.{DisplayYear, Enumerable, FSYears, PaymentOverdue, Year}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import services.financialOverview.{AllPaymentsAndChargesService, PaymentsAndChargesService, PaymentsCache}
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.AFTConstants.QUARTER_START_DATE

import java.time.LocalDate
import scala.concurrent.Future

class SelectYearControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  implicit val config: FrontendAppConfig = mockAppConfig
  val mockPaymentsAndChargesService: AllPaymentsAndChargesService = mock[AllPaymentsAndChargesService]
  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[AllPaymentsAndChargesService].toInstance(mockPaymentsAndChargesService)
  )

  private val paymentsCache: Seq[SchemeFS] => PaymentsCache = schemeFS => PaymentsCache(psaId, srn, schemeDetails, schemeFS)

  private val years: Seq[DisplayYear] = Seq(DisplayYear(2020, Some(PaymentOverdue)))

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()
  val templateToBeRendered = "financialStatement/paymentsAndCharges/selectYear.njk"
  val formProvider = new YearsFormProvider()
  val form: Form[Year] = formProvider()

  lazy val httpPathGET: String = routes.SelectYearController.onPageLoad(srn, AccountingForTaxCharges).url
  lazy val httpPathPOST: String = routes.SelectYearController.onSubmit(srn, AccountingForTaxCharges).url

  private val jsonToPassToTemplate: Form[Year] => JsObject = form => Json.obj(
    "form" -> form,
    "radios" -> FSYears.radios(form, years),
    "schemeName" -> schemeName
  )

  private val year = "2020"

  private val valuesValid: Map[String, Seq[String]] = Map("value" -> Seq(year))
  private val valuesInvalid: Map[String, Seq[String]] = Map("year" -> Seq("20"))

  override def beforeEach: Unit = {
    super.beforeEach
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockPaymentsAndChargesService.isPaymentOverdue).thenReturn(_ => true)
    when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(paymentsCache(schemeFSResponseAftAndOTC)))
  }

  "SelectYear Controller" must {
    "return OK and the correct view for a GET" in {

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate.apply(form))
    }

    "redirect to next page when valid data is submitted and a single quarter is found for the selected year" in {

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result) mustBe Some(routes.AllPaymentsAndChargesController.onPageLoad(srn, QUARTER_START_DATE.toString, AccountingForTaxCharges).url)
    }

    "redirect to next page when valid data is submitted and multiple quarters are found for the selected year" in {
      val schemeFS = schemeFSResponseAftAndOTC.head.copy(periodStartDate = LocalDate.parse("2020-07-01"))
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(schemeFSResponseAftAndOTC :+ schemeFS)))

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

