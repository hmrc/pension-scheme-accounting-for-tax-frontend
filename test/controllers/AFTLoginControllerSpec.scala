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

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.Enumerable
import models.LocalDateBinder._
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.DateHelper

import scala.concurrent.Future

class AFTLoginControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  private def httpPathGET: String = controllers.routes.AFTLoginController.onPageLoad(srn).url

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction

  val application: Application =
    applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build()

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockAppConfig)
    when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(dummyCall.url)
    when(mockAppConfig.overviewApiEnablementDate).thenReturn("2020-07-21")
    when(mockAppConfig.minimumYear).thenReturn(2020)
    mutableFakeDataRetrievalAction.setViewOnly(false)
  }

  "AFTLogin Controller" when {

    "on a GET and overviewApi is disabled i.e before 21st July 2020" must {

      "return to ChargeType page in every case" in {
        DateHelper.setDate(Some(LocalDate.of(2020, 7, 20)))
        mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))

        val result = route(application, httpGETRequest(httpPathGET)).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result) mustBe Some(controllers.routes.ChargeTypeController.onPageLoad(srn, startDate).url)
      }
    }
    "on a GET and overviewApi is enabled i.e after 21st July 2020" must {

      "return to Years page if more than 1 years are available to choose from" in {
        DateHelper.setDate(Some(LocalDate.of(2021, 4, 1)))
        mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))

        val result = route(application, httpGETRequest(httpPathGET)).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result) mustBe Some(controllers.routes.YearsController.onPageLoad(srn).url)
      }

      "return to Quarters page if 1 year and more than 1 quarters are available to choose from" in {
        DateHelper.setDate(Some(LocalDate.of(2020, 8, 2)))
        mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))

        val result = route(application, httpGETRequest(httpPathGET)).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result) mustBe Some(controllers.routes.QuartersController.onPageLoad(srn, "2020").url)
      }

      "return to ChargeType page if exactly 1 year and 1 quarter are available to choose from" in {
        DateHelper.setDate(Some(LocalDate.of(2020, 4, 5)))
        mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))

        val result = route(application, httpGETRequest(httpPathGET)).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result) mustBe Some(controllers.routes.ChargeTypeController.onPageLoad(srn, startDate).url)
      }
    }

  }
}
