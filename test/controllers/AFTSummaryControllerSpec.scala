/*
 * Copyright 2019 HM Revenue & Customs
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

import behaviours.ControllerBehaviours
import controllers.base.ControllerSpecBase
import data.SampleData
import forms.AFTSummaryFormProvider
import matchers.JsonMatchers
import models.{GenericViewModel, NormalMode, UserAnswers}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify}
import pages.AFTSummaryPage
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, route, status}
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}
import utils.AFTSummaryHelper

class AFTSummaryControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with ControllerBehaviours {

  private val templateToBeRendered = "aftSummary.njk"
  private val form = new AFTSummaryFormProvider()()

  private def aftSummaryGetRoute: String = controllers.routes.AFTSummaryController.onPageLoad(NormalMode, SampleData.srn).url

  private def aftSummaryPostRoute: String = controllers.routes.AFTSummaryController.onSubmit(NormalMode, SampleData.srn).url

  private val valuesValid: Map[String, Seq[String]] = Map("value" -> Seq("true"))

  private val valuesInvalid: Map[String, Seq[String]] = Map("value" -> Seq("xyz"))

  private val summaryHelper = new AFTSummaryHelper

  private val jsonToPassToTemplate: Form[Boolean] => JsObject = form => Json.obj(
    "form" -> form,
    "list" -> summaryHelper.summaryListData(UserAnswers(), SampleData.srn),
    "viewModel" -> GenericViewModel(
      submitUrl = routes.AFTSummaryController.onSubmit(NormalMode, SampleData.srn).url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(SampleData.srn),
      schemeName = SampleData.schemeName),
    "radios" -> Radios.yesNo(form("value"))
  )

  "AFTSummary Controller" must {

    behave like controllerWithGETNeverFilledForm(
      httpPath = aftSummaryGetRoute,
      page = AFTSummaryPage,
      data = true,
      form = form,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate
    )

    behave like controllerWithPOST(
      httpPath = aftSummaryPostRoute,
      page = AFTSummaryPage,
      data = true,
      form = form,
      templateToBeRendered = templateToBeRendered,
      requestValuesValid = valuesValid,
      requestValuesInvalid = valuesInvalid
    )
  }
}
