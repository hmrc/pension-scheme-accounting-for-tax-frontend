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
import models.Enumerable
import models.PenaltiesFilter.All
import models.financialStatement.PenaltyType.ContractSettlementCharges
import models.financialStatement.PsaFSChargeType.AFT_INITIAL_LFP
import models.financialStatement.{PenaltyType, PsaFS}
import models.requests.IdentifierRequest
import org.mockito.Matchers.any
import org.mockito.Mockito.{when, _}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.mvc.{Call, Results}
import play.api.test.Helpers.{route, status, _}
import services.{PenaltiesCache, PenaltiesService}
import uk.gov.hmrc.viewmodels.NunjucksSupport

import java.time.LocalDate
import scala.concurrent.Future

class PenaltiesLogicControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  private def httpPathGET: String = routes.PenaltiesLogicController.onPageLoad(All).url

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction
  private val mockPenaltiesService: PenaltiesService = mock[PenaltiesService]
  private val penaltyType: PenaltyType = ContractSettlementCharges
  private val contractSelectYearPage: Call = routes.SelectPenaltiesYearController.onPageLoad(penaltyType, All)

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

      "return to page returned by nav method in penalties service if only one type is available" in {
        when(mockPenaltiesService.getPenaltiesForJourney(any(), any())(any(), any()))
          .thenReturn(Future.successful(PenaltiesCache(psaId, psaName, Seq(psaFS1))))
        when(mockPenaltiesService.navFromOverviewPage(any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(Redirect(contractSelectYearPage)))
        val result = route(application, httpGETRequest(httpPathGET)).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result) mustBe Some(contractSelectYearPage.url)

      }
    }

  }
}

object PenaltiesLogicControllerSpec {

  val psaName: String = "psa-name"
  val psaFS1: PsaFS = PsaFS(
    chargeReference = "XY002610150184",
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
}
