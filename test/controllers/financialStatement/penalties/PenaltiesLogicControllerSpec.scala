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

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import controllers.financialStatement.penalties.PenaltiesLogicControllerSpec._
import data.SampleData._
import matchers.JsonMatchers
import models.{Enumerable, PenaltySchemes}
import models.financialStatement.PsaFS
import models.financialStatement.PsaFSChargeType.{AFT_INITIAL_LFP, OTC_6_MONTH_LPP}
import models.requests.IdentifierRequest
import org.mockito.Matchers.any
import org.mockito.Mockito.{when, _}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import services.PenaltiesService
import uk.gov.hmrc.viewmodels.NunjucksSupport

import java.time.LocalDate
import scala.concurrent.Future

class PenaltiesLogicControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  private def httpPathGET: String = routes.PenaltiesLogicController.onPageLoad().url

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction
  private val mockPenaltiesService: PenaltiesService = mock[PenaltiesService]

  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[PenaltiesService].toInstance(mockPenaltiesService)
  )

  def application: Application = applicationBuilder(extraModules = extraModules).build()

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockPenaltiesService, mockRenderer, mockAppConfig)
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    mutableFakeDataRetrievalAction.setViewOnly(false)
  }

  "Penalties Logic Controller" when {
    "on a GET" must {

      "return to SelectYears page if more than 1 years are available to choose from" in {
        when(mockPenaltiesService.saveAndReturnPenalties(any())(any(), any())).thenReturn(Future.successful(multipleYearsMultipleQuartersPsaFS))
        val result = route(application, httpGETRequest(httpPathGET)).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result) mustBe Some(routes.SelectPenaltiesYearController.onPageLoad().url)

      }

      "return to Quarters page if 1 year and more than 1 quarters are available to choose from" in {
        when(mockPenaltiesService.saveAndReturnPenalties(any())(any(), any())).thenReturn(Future.successful(singleYearMultipleQuartersPsaFS))
        val result = route(application, httpGETRequest(httpPathGET)).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result) mustBe Some(routes.SelectPenaltiesQuarterController.onPageLoad("2020").url)

      }

      "return to SelectScheme page if exactly 1 year, 1 quarter and multiple schemes are available to choose from" in {
        when(mockPenaltiesService.saveAndReturnPenalties(any())(any(), any())).thenReturn(Future.successful(singleYearMSingleQuarterPsaFS))
        when(mockPenaltiesService.penaltySchemes(any(), any())(any(), any()))
          .thenReturn(Future.successful(Seq(PenaltySchemes(Some("ABC"), pstr, Some(srn)), PenaltySchemes(Some("ABC"), pstr, None))))

        val result = route(application, httpGETRequest(httpPathGET)).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result) mustBe Some(routes.SelectSchemeController.onPageLoad("2020-04-01").url)

      }

      "return to Penalties page if exactly 1 year, 1 quarter and 1 scheme with srn is available to choose from" in {
        when(mockPenaltiesService.saveAndReturnPenalties(any())(any(), any())).thenReturn(Future.successful(singleYearMSingleQuarterPsaFS))
        when(mockPenaltiesService.penaltySchemes(any(), any())(any(), any()))
          .thenReturn(Future.successful(Seq(PenaltySchemes(Some("ABC"), pstr, Some(srn)))))

        val result = route(application, httpGETRequest(httpPathGET)).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result) mustBe Some(routes.PenaltiesController.onPageLoad("2020-04-01", srn).url)

      }

      "return to Penalties page if exactly 1 year, 1 quarter and 1 scheme without srn is available to choose from" in {
        when(mockPenaltiesService.saveAndReturnPenalties(any())(any(), any())).thenReturn(Future.successful(singleYearMSingleQuarterPsaFS))
        when(mockPenaltiesService.penaltySchemes(any(), any())(any(), any()))
          .thenReturn(Future.successful(Seq(PenaltySchemes(Some("ABC"), "24000040IN", None))))

        val result = route(application, httpGETRequest(httpPathGET)).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result) mustBe Some(routes.PenaltiesController.onPageLoad("2020-04-01", "0").url)

      }

      "redirect to Session Expired page if there is no data returned from overview" in {
        when(mockPenaltiesService.saveAndReturnPenalties(any())(any(), any())).thenReturn(Future.successful(Nil))

        val result = route(application, httpGETRequest(httpPathGET)).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result) mustBe Some(controllers.routes.SessionExpiredController.onPageLoad().url)

      }
    }

  }
}

object PenaltiesLogicControllerSpec {
  val singleYearMSingleQuarterPsaFS = Seq(psaFS1(), psaFS2("2020-04-01"))
  val singleYearMultipleQuartersPsaFS = Seq(psaFS1(), psaFS1("2020-07-01"), psaFS2(), psaFS1("2020-10-01"))
  val multipleYearsMultipleQuartersPsaFS = Seq(psaFS1(), psaFS2("2021-01-01"))


  def psaFS1(startDate: String = "2020-04-01"): PsaFS = PsaFS(
    chargeReference = "XY002610150184",
    chargeType = AFT_INITIAL_LFP,
    dueDate = Some(LocalDate.parse("2020-07-15")),
    totalAmount = 80000.00,
    outstandingAmount = 56049.08,
    stoodOverAmount = 25089.08,
    amountDue = 1029.05,
    periodStartDate = LocalDate.parse(startDate),
    periodEndDate = LocalDate.parse("2020-06-30"),
    pstr = "24000040IN"
  )

  def psaFS2(startDate: String = "2020-07-01"): PsaFS = PsaFS(
    chargeReference = "XY002610150185",
    chargeType = OTC_6_MONTH_LPP,
    dueDate = Some(LocalDate.parse("2020-02-15")),
    totalAmount = 80000.00,
    outstandingAmount = 56049.08,
    stoodOverAmount = 25089.08,
    amountDue = 1029.05,
    periodStartDate = LocalDate.parse(startDate),
    periodEndDate = LocalDate.parse("2020-09-30"),
    pstr = "24000041IN"
  )
}
