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

package controllers.chargeB

import behaviours.CheckYourAnswersBehaviour
import controllers.base.ControllerSpecBase
import data.SampleData._
import helpers.CYAChargeBHelper
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.UserAnswers
import pages.chargeB.{ChargeBDetailsPage, CheckYourAnswersPage}

class CheckYourAnswersControllerSpec extends ControllerSpecBase with JsonMatchers with CheckYourAnswersBehaviour {

  private def httpGETRoute: String = controllers.chargeB.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt).url
  private def httpOnClickRoute: String = controllers.chargeB.routes.CheckYourAnswersController.onClick(srn, startDate, accessType, versionInt).url

  private def ua: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(ChargeBDetailsPage, chargeBDetails).toOption.get

  private val helper = new CYAChargeBHelper(srn, startDate, accessType, versionInt)

  "CheckYourAnswers Controller" must {
    behave like cyaController(
      httpPath = httpGETRoute,
      chargeName = "chargeB",
      removeChargeUrl = Some(controllers.chargeB.routes.DeleteChargeController.onPageLoad(srn, startDate, accessType, versionInt).url),
      list = helper.chargeBDetails(chargeBDetails),
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
      submitUrl = httpOnClickRoute,
      userAnswers = ua
    )

    behave like controllerWithOnClick(
      httpPath = httpOnClickRoute,
      page = CheckYourAnswersPage,
      userAnswers = ua
    )

    behave like redirectToErrorOn5XX(
      httpPath = httpOnClickRoute,
      page = CheckYourAnswersPage,
      userAnswers = ua
    )
  }
}
