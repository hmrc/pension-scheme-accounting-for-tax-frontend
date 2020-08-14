/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers

import java.time.LocalDate

import audit.{AuditService, StartNewAFTAuditEvent}
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData._
import matchers.JsonMatchers
import models.Enumerable
import models.LocalDateBinder._
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import services.SchemeService
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.DateHelper

import scala.concurrent.Future

class AFTLoginControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  private def httpPathGET: String = controllers.routes.AFTLoginController.onPageLoad(srn).url

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction
  private val expectedAuditEvent = StartNewAFTAuditEvent(SampleData.psaId, SampleData.pstr)


  private val mockSchemeService = mock[SchemeService]
  private val mockAuditService = mock[AuditService]

  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[AuditService].toInstance(mockAuditService),
    bind[SchemeService].toInstance(mockSchemeService)
  )

  val application: Application =
    applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockAppConfig, mockAuditService, mockSchemeService)
    when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(dummyCall.url)
    when(mockAppConfig.minimumYear).thenReturn(2020)
    mutableFakeDataRetrievalAction.setViewOnly(false)
    when(mockSchemeService.retrieveSchemeDetails(any(),any())(any(), any())).thenReturn(Future.successful(schemeDetails))
  }

  "AFTLogin Controller on a GET" must {

      "return to Years page if more than 1 years are available to choose from and send audit event" in {
        DateHelper.setDate(Some(LocalDate.of(2021, 4, 1)))
        mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))
        val eventCaptor = ArgumentCaptor.forClass(classOf[StartNewAFTAuditEvent])

        val result = route(application, httpGETRequest(httpPathGET)).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result) mustBe Some(controllers.routes.YearsController.onPageLoad(srn).url)
        verify(mockAuditService, times(1)).sendEvent(eventCaptor.capture())(any(), any())
        eventCaptor.getValue mustBe expectedAuditEvent
      }

      "return to Quarters page if 1 year and more than 1 quarters are available to choose from and send audit event" in {
        DateHelper.setDate(Some(LocalDate.of(2020, 8, 2)))
        mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))
        val eventCaptor = ArgumentCaptor.forClass(classOf[StartNewAFTAuditEvent])

        val result = route(application, httpGETRequest(httpPathGET)).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result) mustBe Some(controllers.routes.QuartersController.onPageLoad(srn, "2020").url)
        verify(mockAuditService, times(1)).sendEvent(eventCaptor.capture())(any(), any())
        eventCaptor.getValue mustBe expectedAuditEvent
      }

      "return to ChargeType page if exactly 1 year and 1 quarter are available to choose from and send audit event" in {
        DateHelper.setDate(Some(LocalDate.of(2020, 4, 5)))
        mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))
        val eventCaptor = ArgumentCaptor.forClass(classOf[StartNewAFTAuditEvent])
        val result = route(application, httpGETRequest(httpPathGET)).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result) mustBe Some(controllers.routes.ChargeTypeController.onPageLoad(srn, startDate, accessType, versionInt).url)
        verify(mockAuditService, times(1)).sendEvent(eventCaptor.capture())(any(), any())
        eventCaptor.getValue mustBe expectedAuditEvent
      }

  }
}
