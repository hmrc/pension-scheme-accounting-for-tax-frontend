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

import audit.{AuditService, StartAFTAuditEvent}
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData._
import matchers.JsonMatchers
import models.{Enumerable, NormalMode}
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, Matchers}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import pages.IsPsaSuspendedQuery
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import services.{AFTService, AllowAccessService}
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.DateHelper

import scala.concurrent.Future

class AFTLoginControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  private def httpPathGET: String = controllers.routes.AFTLoginController.onPageLoad(SampleData.srn).url

  private val mockAuditService = mock[AuditService]
  private val mockAllowAccessService = mock[AllowAccessService]
  private val mockAFTService = mock[AFTService]
  private val retrievedUA = userAnswersWithSchemeName
    .setOrException(IsPsaSuspendedQuery, value = false)
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction

  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[AuditService].toInstance(mockAuditService),
    bind[AllowAccessService].toInstance(mockAllowAccessService),
    bind[AFTService].toInstance(mockAFTService)
  )

  val application: Application =
    applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockAllowAccessService, mockUserAnswersCacheConnector, mockRenderer, mockAFTService, mockAppConfig)
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAllowAccessService.filterForIllegalPageAccess(any(), any())(any())).thenReturn(Future.successful(None))
    when(mockAFTService.retrieveAFTRequiredDetails(any(), any())(any(), any(), any())).thenReturn(Future.successful((schemeDetails, retrievedUA)))
    when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(dummyCall.url)
    mutableFakeDataRetrievalAction.setViewOnly(false)
  }

  "AFTLogin Controller" when {
    "on a GET" must {

      "redirect to aft summary page when the user is locked and coming to charge type page" in {
        mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))
        mutableFakeDataRetrievalAction.setViewOnly(true)

        val result = route(application, httpGETRequest(httpPathGET)).value

        redirectLocation(result).value mustBe controllers.routes.AFTSummaryController.onPageLoad(srn, None).url

        verify(mockRenderer, never()).render(any(), any())(any())
        verify(mockAFTService, never()).retrieveAFTRequiredDetails(Matchers.eq(srn), Matchers.eq(None))(any(), any(), any())
        verify(mockAllowAccessService, never()).filterForIllegalPageAccess(Matchers.eq(srn), Matchers.eq(retrievedUA))(any())
      }

      "redirect to alternative location when allow access service returns alternative location" in {
        reset(mockAllowAccessService)
        val location = "redirect"
        mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))
        when(mockAllowAccessService.filterForIllegalPageAccess(any(), any())(any()))
          .thenReturn(Future.successful(Some(Redirect(location))))

        val result = route(application, httpGETRequest(httpPathGET)).value
        status(result) mustEqual SEE_OTHER
        redirectLocation(result) mustBe Some(location)
        }
      }

      "send the AFTStart Audit Event" in {
        reset(mockAuditService)
        DateHelper.setDate(Some(LocalDate.of(2020, 4, 1)))
        val eventCaptor = ArgumentCaptor.forClass(classOf[StartAFTAuditEvent])
        mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))

        val result = route(application, httpGETRequest(httpPathGET)).value

        status(result) mustEqual SEE_OTHER

        verify(mockAuditService, times(1)).sendEvent(eventCaptor.capture())(any(), any())
        eventCaptor.getValue mustEqual StartAFTAuditEvent(SampleData.psaId, SampleData.pstr)
      }

      "return to Years page if more than 1 years are available to choose from" in {
        DateHelper.setDate(Some(LocalDate.of(2021, 4, 1)))
        mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))

        val result = route(application, httpGETRequest(httpPathGET)).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result) mustBe Some(controllers.routes.YearsController.onPageLoad(srn).url)

        verify(mockAFTService, times(1)).retrieveAFTRequiredDetails(Matchers.eq(srn), Matchers.eq(None))(any(), any(), any())
        verify(mockAllowAccessService, times(1)).filterForIllegalPageAccess(Matchers.eq(srn), Matchers.eq(retrievedUA))(any())

      }

    "return to Quarters page if 1 year and more than 1 quarters are available to choose from" in {
      DateHelper.setDate(Some(LocalDate.of(2020, 8, 2)))
      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.QuartersController.onPageLoad(srn).url)

      verify(mockAFTService, times(1)).retrieveAFTRequiredDetails(Matchers.eq(srn), Matchers.eq(None))(any(), any(), any())
      verify(mockAllowAccessService, times(1)).filterForIllegalPageAccess(Matchers.eq(srn), Matchers.eq(retrievedUA))(any())

    }

    "return to ChargeType page if exactly 1 year and 1 quarter are available to choose from" in {
      DateHelper.setDate(Some(LocalDate.of(2020, 4, 5)))
      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.ChargeTypeController.onPageLoad(NormalMode, srn).url)

      verify(mockAFTService, times(1)).retrieveAFTRequiredDetails(Matchers.eq(srn), Matchers.eq(None))(any(), any(), any())
      verify(mockAllowAccessService, times(1)).filterForIllegalPageAccess(Matchers.eq(srn), Matchers.eq(retrievedUA))(any())

    }

  }
}
