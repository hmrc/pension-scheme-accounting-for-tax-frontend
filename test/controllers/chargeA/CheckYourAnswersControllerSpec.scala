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

package controllers.chargeA

import behaviours.CheckYourAnswersBehaviour
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.GenericViewModel
import models.UserAnswers
import pages.chargeA.ChargeDetailsPage
import pages.chargeA.CheckYourAnswersPage
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.CheckYourAnswersHelper
import models.LocalDateBinder._

class CheckYourAnswersControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with CheckYourAnswersBehaviour {

  private val templateToBeRendered = "check-your-answers.njk"
  private val helper: CheckYourAnswersHelper = new CheckYourAnswersHelper(ua, srn, startDate)
  private val jsonToPassToTemplate: JsObject = Json.obj(
    "list" -> Seq(
      helper.chargeAMembers(chargeAChargeDetails),
      helper.chargeAAmountLowerRate(chargeAChargeDetails),
      helper.chargeAAmountHigherRate(chargeAChargeDetails),
      helper.total(ua.get(ChargeDetailsPage).map(_.totalAmount).getOrElse(BigDecimal(0)))
    ),
    "viewModel" -> GenericViewModel(
      submitUrl = routes.CheckYourAnswersController.onClick(srn, startDate).url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(srn, startDate),
      schemeName = schemeName
    ),
    "chargeName" -> "chargeA"
  )

  private def httpGETRoute: String = controllers.chargeA.routes.CheckYourAnswersController.onPageLoad(srn, startDate).url

  private def httpOnClickRoute: String = controllers.chargeA.routes.CheckYourAnswersController.onClick(srn, startDate).url

  private def ua: UserAnswers = userAnswersWithSchemeNamePstrQuarter.set(ChargeDetailsPage, chargeAChargeDetails).toOption.get

  "CheckYourAnswers Controller" must {
    behave like cyaController(
      httpPath = httpGETRoute,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate,
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
