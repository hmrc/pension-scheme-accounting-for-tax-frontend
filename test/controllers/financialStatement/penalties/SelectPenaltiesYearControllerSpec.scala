/*
 * Copyright 2021 HM Revenue & Customs
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

package controllers.financialStatement.penalties

import config.FrontendAppConfig
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.YearsFormProvider
import matchers.JsonMatchers
import models.financialStatement.PsaFS
import models.financialStatement.PsaFSChargeType.OTC_6_MONTH_LPP
import models.requests.IdentifierRequest
import models.{AmendYears, Enumerable, Year}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito._
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
import services.PenaltiesService
import uk.gov.hmrc.viewmodels.NunjucksSupport

import java.time.LocalDate
import scala.concurrent.Future

class SelectPenaltiesYearControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  implicit val config: FrontendAppConfig = mockAppConfig
  val mockPenaltiesService: PenaltiesService = mock[PenaltiesService]
  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[PenaltiesService].toInstance(mockPenaltiesService)
  )

  private val years: Seq[Int] = Seq(2021, 2020)
  private val psaFsSeq: Seq[PsaFS] = Seq(
    PsaFS(
      chargeReference = "XY002610150184",
      chargeType = OTC_6_MONTH_LPP,
      dueDate = Some(LocalDate.parse("2020-11-15")),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = 100.00,
      periodStartDate = LocalDate.parse("2020-07-01"),
      periodEndDate = LocalDate.parse("2020-09-30"),
      pstr = "24000041IN"
    ),
    PsaFS(
      chargeReference = "XY002610150184",
      chargeType = OTC_6_MONTH_LPP,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = 100.00,
      periodStartDate = LocalDate.parse("2020-10-01"),
      periodEndDate = LocalDate.parse("2020-12-31"),
      pstr = "24000041IN"
    )
  )
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()
  val templateToBeRendered = "financialStatement/penalties/selectYear.njk"
  val formProvider = new YearsFormProvider()
  val form: Form[Year] = formProvider()

  lazy val httpPathGET: String = routes.SelectPenaltiesYearController.onPageLoad().url
  lazy val httpPathPOST: String = routes.SelectPenaltiesYearController.onSubmit().url

  private val jsonToPassToTemplate: Form[Year] => JsObject = form => Json.obj(
    "form" -> form,
    "radios" -> AmendYears.radios(form, years),
    "submitUrl" -> httpPathPOST
  )

  private val year = "2020"

  private val valuesValid: Map[String, Seq[String]] = Map("value" -> Seq(year))
  private val valuesInvalid: Map[String, Seq[String]] = Map("year" -> Seq("20"))

  override def beforeEach: Unit = {
    super.beforeEach
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockPenaltiesService.saveAndReturnPenalties(any())(any(), any())).thenReturn(Future.successful(years))
    when(mockPenaltiesService.getPenaltiesFromCache(any(), any())).thenReturn(Future.successful(psaFsSeq))
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

    "redirect to next page when valid data is submitted" in {

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result) mustBe Some(controllers.routes.QuartersController.onPageLoad(srn, year).url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
    }
  }
}

