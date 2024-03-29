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
import models.{GenericViewModel, UserAnswers}
import pages.chargeA.{ChargeDetailsPage, CheckYourAnswersPage}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.viewmodels.NunjucksSupport

class CheckYourAnswersControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with CheckYourAnswersBehaviour {

  private val templateToBeRendered = "check-your-answers.njk"

  private def httpGETRoute: String = controllers.chargeA.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt).url

  private def httpOnClickRoute: String = controllers.chargeA.routes.CheckYourAnswersController.onClick(srn, startDate, accessType, versionInt).url

  private def ua: UserAnswers = userAnswersWithSchemeNamePstrQuarter.set(ChargeDetailsPage, chargeAChargeDetails).toOption.get

  private val helper: CYAChargeAHelper = new CYAChargeAHelper(srn, startDate, accessType, versionInt)

  private val jsonToPassToTemplate: JsObject = Json.obj(
    "list" -> Seq(
      helper.chargeAMembers(chargeAChargeDetails),
      helper.chargeAAmountLowerRate(chargeAChargeDetails),
      helper.chargeAAmountHigherRate(chargeAChargeDetails),
      helper.total(ua.get(ChargeDetailsPage).map(_.totalAmount).getOrElse(BigDecimal(0)))
    ),
    "viewModel" -> GenericViewModel(
      submitUrl = routes.CheckYourAnswersController.onClick(srn, startDate, accessType, versionInt).url,
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
      schemeName = schemeName
    ),
    "chargeName" -> "chargeA"
  )

  "CheckYourAnswers Controller" must {
    behave like cyaController(
      httpPath = httpGETRoute,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate,
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
