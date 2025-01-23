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

import behaviours.CheckYourAnswersBehaviour
import controllers.base.ControllerSpecBase
import data.SampleData._
import helpers.CYAChargeGHelper
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.UserAnswers
import pages.chargeG.{ChargeAmountsPage, ChargeDetailsPage, CheckYourAnswersPage, MemberDetailsPage}

class CheckYourAnswersControllerSpec extends ControllerSpecBase with JsonMatchers with CheckYourAnswersBehaviour {


  private def httpGETRoute: String = controllers.chargeG.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt, 0).url
  private def httpOnClickRoute: String = controllers.chargeG.routes.CheckYourAnswersController.onClick(srn, startDate, accessType, versionInt, 0).url

  private def ua: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(MemberDetailsPage(0), memberGDetails).toOption.get
    .set(ChargeDetailsPage(0), chargeGDetails).toOption.get
    .set(ChargeAmountsPage(0), chargeAmounts).toOption.get

  private val helper = new CYAChargeGHelper(srn, startDate, accessType, versionInt)
  private val rows = Seq(
    helper.chargeGMemberDetails(0, memberGDetails),
    helper.chargeGDetails(0, chargeGDetails),
    helper.chargeGAmounts(0, chargeAmounts)
  ).flatten

  "CheckYourAnswers Controller" must {
    behave like cyaController(
      httpPath = httpGETRoute,
      chargeName = "chargeG",
      list = rows,
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
