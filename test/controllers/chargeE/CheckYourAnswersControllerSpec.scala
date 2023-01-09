/*
 * Copyright 2023 HM Revenue & Customs
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

package controllers.chargeE

import behaviours.CheckYourAnswersBehaviour
import controllers.base.ControllerSpecBase
import data.SampleData._
import helpers.CYAChargeEHelper
import matchers.JsonMatchers
import models.ChargeType.ChargeTypeAnnualAllowance
import models.LocalDateBinder._
import models.mccloud.{PensionsRemedySchemeSummary, PensionsRemedySummary}
import models.{UserAnswers, YearRange}
import pages.chargeE.{AnnualAllowanceYearPage, ChargeDetailsPage, CheckYourAnswersPage, MemberDetailsPage}
import pages.mccloud._
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.DateHelper

import java.time.LocalDate

class CheckYourAnswersControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with CheckYourAnswersBehaviour {
  //scalastyle:off magic.number

  private val dynamicYearRange = YearRange("2019")

  private val templateToBeRendered = "check-your-answers.njk"
  private val annualAllowanceCharge = ChargeTypeAnnualAllowance

  private def httpGETRoute: String = controllers.chargeE.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt, 0).url

  private def httpOnClickRoute: String = controllers.chargeE.routes.CheckYourAnswersController.onClick(srn, startDate, accessType, versionInt, 0).url

  val pensionsRemedySchemeSummaryEmpty = List()

  val pensionsRemedySchemeSummaryWithPstr = List(PensionsRemedySchemeSummary(schemeIndex, Some(pstrNumber), Some(dynamicYearRange), Some(taxQuarter)
    , Some(chargeAmountReported)))

  val pensionsRemedySchemeSummary = List(PensionsRemedySchemeSummary(schemeIndex, None, Some(dynamicYearRange), Some(taxQuarter)
    , Some(chargeAmountReported)))

  private def ua: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(MemberDetailsPage(0), memberDetails).toOption.get
    .set(AnnualAllowanceYearPage(0), dynamicYearRange).toOption.get
    .set(ChargeDetailsPage(0), chargeEDetails).toOption.get

  private val helper = new CYAChargeEHelper(srn, startDate, accessType, versionInt)

  def pstrSummary(isPSR: Boolean, isChargeInAddition: Boolean, wasAnotherPensionScheme: Boolean) = {

    (isPSR, isChargeInAddition, wasAnotherPensionScheme) match {
      case (false, false, false) =>
        PensionsRemedySummary(Some(isPSR),
          Some(isChargeInAddition), Some(wasAnotherPensionScheme), pensionsRemedySchemeSummaryEmpty)
      case (true, false, false) =>
        PensionsRemedySummary(Some(isPSR),
          Some(isChargeInAddition), Some(wasAnotherPensionScheme), pensionsRemedySchemeSummaryEmpty)
      case (true, true, false) =>
        PensionsRemedySummary(Some(isPSR),
          Some(isChargeInAddition), Some(wasAnotherPensionScheme), pensionsRemedySchemeSummary)
      case (true, true, true) =>
        PensionsRemedySummary(Some(isPSR),
          Some(isChargeInAddition), Some(wasAnotherPensionScheme), pensionsRemedySchemeSummaryWithPstr)
    }
  }

  private def updateUserAnswers(ua: UserAnswers, isPSR: Boolean, isChargeInAddition: Boolean, wasAnotherPensionScheme: Boolean): UserAnswers = {

    (isPSR, isChargeInAddition, wasAnotherPensionScheme) match {
      case (false, false, false) => ua.setOrException(IsPublicServicePensionsRemedyPage(annualAllowanceCharge, 0), isPSR)
      case (true, false, false) =>
        ua.setOrException(IsPublicServicePensionsRemedyPage(annualAllowanceCharge, 0), isPSR)
          .setOrException(IsChargeInAdditionReportedPage(annualAllowanceCharge, 0), isChargeInAddition)
      case (true, true, false) =>
        ua.setOrException(IsPublicServicePensionsRemedyPage(annualAllowanceCharge, 0), isPSR)
          .setOrException(IsChargeInAdditionReportedPage(annualAllowanceCharge, 0), isChargeInAddition)
          .setOrException(WasAnotherPensionSchemePage(annualAllowanceCharge, 0), wasAnotherPensionScheme)
          .setOrException(TaxYearReportedAndPaidPage(annualAllowanceCharge, 0, None), dynamicYearRange)
          .setOrException(TaxQuarterReportedAndPaidPage(annualAllowanceCharge, 0, None), taxQuarter)
          .setOrException(ChargeAmountReportedPage(annualAllowanceCharge, 0, None), chargeAmountReported)
      case (true, true, true) =>
        ua.setOrException(IsPublicServicePensionsRemedyPage(annualAllowanceCharge, 0), isPSR)
          .setOrException(IsChargeInAdditionReportedPage(annualAllowanceCharge, 0), isChargeInAddition)
          .setOrException(WasAnotherPensionSchemePage(annualAllowanceCharge, 0), wasAnotherPensionScheme)
          .setOrException(EnterPstrPage(annualAllowanceCharge, 0, 0), pstrNumber)
          .setOrException(TaxYearReportedAndPaidPage(annualAllowanceCharge, 0, Some(0)), dynamicYearRange)
          .setOrException(TaxQuarterReportedAndPaidPage(annualAllowanceCharge, 0, Some(0)), taxQuarter)
          .setOrException(ChargeAmountReportedPage(annualAllowanceCharge, 0, Some(0)), chargeAmountReported)
    }

  }

  private def rows(isPSR: Boolean, isChargeInAddition: Boolean, wasAnotherPensionScheme: Boolean) = {

    Seq(
      helper.isPsprForChargeE(0, Some(isPSR)),
      helper.chargeEMemberDetails(0, memberDetails),
      helper.chargeETaxYear(0, dynamicYearRange),
      helper.chargeEDetails(0, chargeEDetails),
      helper.psprChargeEDetails(0, pstrSummary(isPSR, isChargeInAddition, wasAnotherPensionScheme)).getOrElse(None),
      helper.psprSchemesChargeEDetails(0, pstrSummary(isPSR, isChargeInAddition, wasAnotherPensionScheme), wasAnotherPensionScheme)
    ).flatten
  }

  private def jsonToPassToTemplate(isPSR: Boolean, isChargeInAddition: Boolean, wasAnotherPensionScheme: Boolean): JsObject = Json.obj(
    "list" -> rows(isPSR, isChargeInAddition, wasAnotherPensionScheme)
  )

  DateHelper.setDate(Some(LocalDate.of(2020, 4, 1)))

  "CheckYourAnswers Controller for (false, false, false)" must {

    behave like cyaController(
      httpPath = httpGETRoute,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate(false, false, false),
      userAnswers = updateUserAnswers(ua, false, false, false)
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

  "CheckYourAnswers Controller for (true, false, false)" must {

    behave like cyaController(
      httpPath = httpGETRoute,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate(true, false, false),
      userAnswers = updateUserAnswers(ua, true, false, false)
    )
  }

  "CheckYourAnswers Controller for PSR (true, true, false)" must {

    behave like cyaController(
      httpPath = httpGETRoute,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate(true, true, false),
      userAnswers = updateUserAnswers(ua, true, true, false)
    )
  }

  "CheckYourAnswers Controller for PSR (true, true, true)" must {

    behave like cyaController(
      httpPath = httpGETRoute,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate(true, true, true),
      userAnswers = updateUserAnswers(ua, true, true, true)
    )
  }
}
