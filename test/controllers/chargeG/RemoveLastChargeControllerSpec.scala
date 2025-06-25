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

package controllers.chargeG

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.{CheckMode, Index, UserAnswers}
import play.api.Application
import play.api.test.Helpers.{route, status, _}
import utils.AFTConstants.QUARTER_START_DATE
import views.html.RemoveLastChargeView


class RemoveLastChargeControllerSpec extends ControllerSpecBase with JsonMatchers {
  private val userAnswers: Option[UserAnswers] = Some(userAnswersWithSchemeNamePstrQuarter)
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = registerApp(applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build())
  private val index = Index(0)

  private def httpPathGET: String = controllers.chargeG.routes.RemoveLastChargeController.onPageLoad(srn, startDate, accessType, versionInt, index).url

  val redirectUrl: String = routes.ChargeAmountsController.onSubmit(CheckMode, srn, startDate, accessType, versionInt, index).url

  override def beforeEach(): Unit = {
    super.beforeEach()  }

  "removeLastCharge Controller" must {
    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      val request = httpGETRequest(httpPathGET)

      val view = application.injector.instanceOf[RemoveLastChargeView].apply(
        submitCall = redirectUrl,
        returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, QUARTER_START_DATE, accessType, versionInt).url,
        schemeName = schemeName
      )(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }
  }
}
