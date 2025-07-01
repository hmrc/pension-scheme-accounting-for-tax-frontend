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

package controllers.amend

import audit.{AuditService, StartAmendAFTAuditEvent}
import connectors.AFTConnector
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData._
import matchers.JsonMatchers
import models.requests.IdentifierRequest
import models.{Enumerable, SchemeDetails, SchemeStatus}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import services.SchemeService

import scala.concurrent.Future

class AFTAmendControllerSpec extends ControllerSpecBase with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  private def httpPathGET: String = controllers.amend.routes.AFTAmendController.onPageLoad(srn).url

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction
  private val expectedAuditEvent = StartAmendAFTAuditEvent(SampleData.psaId, SampleData.pstr)

  private val mockSchemeService: SchemeService = mock[SchemeService]
  private val mockAFTConnector: AFTConnector = mock[AFTConnector]
  private val mockAuditService = mock[AuditService]

  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[SchemeService].toInstance(mockSchemeService),
    bind[AFTConnector].toInstance(mockAFTConnector),
    bind[AuditService].toInstance(mockAuditService)
  )

  def application: Application = applicationBuilder(extraModules = extraModules).build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockSchemeService)
    reset(mockAppConfig)
    reset(mockAuditService)
    when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any()))
      .thenReturn(Future.successful(SchemeDetails("Big Scheme", "pstr", SchemeStatus.Open.toString, None)))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[?])).thenReturn(dummyCall.url)
    mutableFakeDataRetrievalAction.setViewOnly(false)
  }

  "AFTAmend Controller" when {
    "on a GET" must {

      "return to AmendYears page if more than 1 years are available to choose from" in {
        when(mockAFTConnector.getAftOverview(any(), any(), any(),  any(), any())(any(), any())).thenReturn(Future.successful(Seq(overview1, overview2, overview3)))
        val result = route(application, httpGETRequest(httpPathGET)).value
        val eventCaptor = ArgumentCaptor.forClass(classOf[StartAmendAFTAuditEvent])

        status(result) mustEqual SEE_OTHER
        redirectLocation(result) mustBe Some(controllers.amend.routes.AmendYearsController.onPageLoad(srn).url)
        verify(mockAuditService, times(1)).sendEvent(eventCaptor.capture())(any(), any())
        eventCaptor.getValue mustBe expectedAuditEvent
      }

      "return to Quarters page if 1 year and more than 1 quarters are available to choose from" in {
        when(mockAFTConnector.getAftOverview(any(), any(), any(),  any(), any())(any(), any())).thenReturn(Future.successful(Seq(overview1, overview2)))
        val result = route(application, httpGETRequest(httpPathGET)).value
        val eventCaptor = ArgumentCaptor.forClass(classOf[StartAmendAFTAuditEvent])

        status(result) mustEqual SEE_OTHER
        redirectLocation(result) mustBe Some(controllers.amend.routes.AmendQuartersController.onPageLoad(srn, "2020").url)
        verify(mockAuditService, times(1)).sendEvent(eventCaptor.capture())(any(), any())
        eventCaptor.getValue mustBe expectedAuditEvent
      }

      "return to ReturnHistory page if exactly 1 year and 1 quarter are available to choose from" in {
        when(mockAFTConnector.getAftOverview(any(), any(), any(),  any(), any())(any(), any())).thenReturn(Future.successful(Seq(overview1)))

        val result = route(application, httpGETRequest(httpPathGET)).value
        val eventCaptor = ArgumentCaptor.forClass(classOf[StartAmendAFTAuditEvent])

        status(result) mustEqual SEE_OTHER
        redirectLocation(result) mustBe Some(controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, "2020-04-01").url)
        verify(mockAuditService, times(1)).sendEvent(eventCaptor.capture())(any(), any())
        eventCaptor.getValue mustBe expectedAuditEvent
      }

      "redirect to Session Expired page if there is no data returned from overview" in {
        when(mockAFTConnector.getAftOverview(any(), any(), any(),  any(), any())(any(), any())).thenReturn(Future.successful(Nil))

        val result = route(application, httpGETRequest(httpPathGET)).value
        val eventCaptor = ArgumentCaptor.forClass(classOf[StartAmendAFTAuditEvent])

        status(result) mustEqual SEE_OTHER
        redirectLocation(result) mustBe Some(controllers.routes.SessionExpiredController.onPageLoad.url)
        verify(mockAuditService, times(1)).sendEvent(eventCaptor.capture())(any(), any())
        eventCaptor.getValue mustBe expectedAuditEvent
      }
    }

  }
}
