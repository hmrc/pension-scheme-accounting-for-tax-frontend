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

package controllers.chargeA

import behaviours.CheckYourAnswersBehaviour
import controllers.base.ControllerSpecBase
import data.SampleData._
import helpers.CYAChargeAHelper
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.UserAnswers
import pages.chargeA.{ChargeDetailsPage, CheckYourAnswersPage}

class CheckYourAnswersControllerSpec extends ControllerSpecBase with JsonMatchers with CheckYourAnswersBehaviour {

  private def httpGETRoute: String = controllers.chargeA.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt).url

  private def httpOnClickRoute: String = controllers.chargeA.routes.CheckYourAnswersController.onClick(srn, startDate, accessType, versionInt).url

  private def ua: UserAnswers = userAnswersWithSchemeNamePstrQuarter.set(ChargeDetailsPage, chargeAChargeDetails).toOption.get

  private val helper: CYAChargeAHelper = new CYAChargeAHelper(srn, startDate, accessType, versionInt)

  private val rows = Seq(
    helper.chargeADetails(chargeAChargeDetails),
    helper.total(ua.get(ChargeDetailsPage).map(_.totalAmount).getOrElse(BigDecimal(0)))
  )

  "CheckYourAnswers Controller" must {
    behave like cyaController(
      httpPath = httpGETRoute,
      chargeName = "chargeA",
      removeChargeUrl = Some(controllers.chargeA.routes.DeleteChargeController.onPageLoad(srn, startDate, accessType, versionInt).url),
      list = rows,
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
      submitUrl = routes.CheckYourAnswersController.onClick(srn, startDate, accessType, versionInt).url,
      userAnswers = ua
    )

    behave like redirectToErrorOn5XX(
      httpPath = httpOnClickRoute,
      page = CheckYourAnswersPage,
      userAnswers = ua
    )
  }

  "CheckYourAnswers Controller with both rates of tax set" must {
    behave like controllerWithOnClick(
      httpPath = httpOnClickRoute,
      page = CheckYourAnswersPage,
      userAnswers = ua
    )
  }

  "CheckYourAnswers Controller with no lower rate of tax set" must {
    behave like controllerWithOnClick(
      httpPath = httpOnClickRoute,
      page = CheckYourAnswersPage,
      userAnswers = ua.set(ChargeDetailsPage, chargeAChargeDetails.copy(totalAmtOfTaxDueAtLowerRate = None)).get
    )
  }

  "CheckYourAnswers Controller with no higher rate of tax set" must {
    behave like controllerWithOnClick(
      httpPath = httpOnClickRoute,
      page = CheckYourAnswersPage,
      userAnswers = ua.set(ChargeDetailsPage, chargeAChargeDetails.copy(totalAmtOfTaxDueAtHigherRate = None)).get
    )
  }
}
