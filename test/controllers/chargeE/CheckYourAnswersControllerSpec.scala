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
import pages.IsPublicServicePensionsRemedyPage
import pages.chargeE.{AnnualAllowanceYearPage, ChargeDetailsPage, CheckYourAnswersPage, MemberDetailsPage}
import pages.mccloud._
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.DateHelper

import java.time.LocalDate
import scala.collection.Seq

class CheckYourAnswersControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with CheckYourAnswersBehaviour {
  //scalastyle:off magic.number

  private val dynamicYearRange = YearRange("2019")

  private val templateToBeRendered = "check-your-answers.njk"
  private val annualAllowanceCharge = ChargeTypeAnnualAllowance

  private def httpGETRoute: String = controllers.chargeE.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt, 0).url

  private def httpOnClickRoute: String = controllers.chargeE.routes.CheckYourAnswersController.onClick(srn, startDate, accessType, versionInt, 0).url

  val pensionsRemedySchemeSummaryEmpty: List[Nothing] = List()

  val pensionsRemedySchemeSummaryWithPstr: List[PensionsRemedySchemeSummary] =
    List(PensionsRemedySchemeSummary(schemeIndex, Some(pstrNumber), Some(dynamicYearRange), Some(taxQuarter), Some(chargeAmountReported)))

  val pensionsRemedySchemeSummary: List[PensionsRemedySchemeSummary] =
    List(PensionsRemedySchemeSummary(schemeIndex, None, Some(dynamicYearRange), Some(taxQuarter), Some(chargeAmountReported)))

  private def ua: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(MemberDetailsPage(0), memberDetails).toOption.get
    .set(AnnualAllowanceYearPage(0), dynamicYearRange).toOption.get
    .set(ChargeDetailsPage(0), chargeEDetails).toOption.get

  private val helper = new CYAChargeEHelper(srn, startDate, accessType, versionInt)

  def psprSummary(isPSR: Boolean, isChargeInAddition: Boolean, wasAnotherPensionScheme: Boolean): PensionsRemedySummary = {

    (isPSR, isChargeInAddition, wasAnotherPensionScheme) match {
      case (false, _, _) =>
        PensionsRemedySummary(Some(isPSR),
          Some(isChargeInAddition), Some(wasAnotherPensionScheme), pensionsRemedySchemeSummaryEmpty)
      case (true, false, _) =>
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
      case (false, _, _) => ua.setOrException(IsPublicServicePensionsRemedyPage(annualAllowanceCharge, Some(0)), isPSR)
      case (true, false, _) =>
        ua.setOrException(IsPublicServicePensionsRemedyPage(annualAllowanceCharge, Some(0)), isPSR)
          .setOrException(IsChargeInAdditionReportedPage(annualAllowanceCharge, 0), isChargeInAddition)
      case (true, true, false) =>
        ua.setOrException(IsPublicServicePensionsRemedyPage(annualAllowanceCharge, Some(0)), isPSR)
          .setOrException(IsChargeInAdditionReportedPage(annualAllowanceCharge, 0), isChargeInAddition)
          .setOrException(WasAnotherPensionSchemePage(annualAllowanceCharge, 0), wasAnotherPensionScheme)
          .setOrException(TaxYearReportedAndPaidPage(annualAllowanceCharge, 0, None), dynamicYearRange)
          .setOrException(TaxQuarterReportedAndPaidPage(annualAllowanceCharge, 0, None), taxQuarter)
          .setOrException(ChargeAmountReportedPage(annualAllowanceCharge, 0, None), chargeAmountReported)
      case (true, true, true) =>
        ua.setOrException(IsPublicServicePensionsRemedyPage(annualAllowanceCharge, Some(0)), isPSR)
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
      helper.isPsprForCharge(0, Some(isPSR)),
      helper.chargeEMemberDetails(0, memberDetails),
      helper.chargeETaxYear(0, dynamicYearRange),
      helper.chargeEDetails(0, chargeEDetails),
      helper.psprChargeDetails(0, psprSummary(isPSR, isChargeInAddition, wasAnotherPensionScheme)).getOrElse(None),
      helper.psprSchemesChargeDetails(0, psprSummary(isPSR, isChargeInAddition, wasAnotherPensionScheme), wasAnotherPensionScheme)
    ).flatten
  }

  private def jsonToPassToTemplate(isPSR: Boolean, isChargeInAddition: Boolean, wasAnotherPensionScheme: Boolean): JsObject = Json.obj(
    "list" -> rows(isPSR, isChargeInAddition, wasAnotherPensionScheme)
  )

  private val rowsWithoutPSR = Seq(
    helper.chargeEMemberDetails(0, memberDetails),
    helper.chargeETaxYear(0, dynamicYearRange),
    helper.chargeEDetails(0, chargeEDetails)
  ).flatten

  private val jsonToPassToTemplateNoPSR: JsObject = Json.obj(
    "list" -> rowsWithoutPSR
  )

  DateHelper.setDate(Some(LocalDate.of(2020, 4, 1)))

  "CheckYourAnswers Controller for PSR if isPSR is false, isChargeInAddition is false and wasAnotherPensionScheme is false" must {

    behave like cyaController(
      httpPath = httpGETRoute,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate(isPSR = false, isChargeInAddition = false, wasAnotherPensionScheme = false),
      userAnswers = updateUserAnswers(ua, isPSR = false, isChargeInAddition = false, wasAnotherPensionScheme = false)
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

  "CheckYourAnswers Controller without PSR Questions" must {

    behave like cyaController(
      httpPath = httpGETRoute,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplateNoPSR,
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

  "CheckYourAnswers Controller for  PSR if isPSR is true, isChargeInAddition is false and wasAnotherPensionScheme is false" must {

    behave like cyaController(
      httpPath = httpGETRoute,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate(isPSR = true, isChargeInAddition = false, wasAnotherPensionScheme = false),
      userAnswers = updateUserAnswers(ua, isPSR = true, isChargeInAddition = false, wasAnotherPensionScheme = false)
    )
  }

  "CheckYourAnswers Controller for PSR if isPSR is true, isChargeInAddition is true and wasAnotherPensionScheme is false" must {

    behave like cyaController(
      httpPath = httpGETRoute,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate(isPSR = true, isChargeInAddition = true, wasAnotherPensionScheme = false),
      userAnswers = updateUserAnswers(ua, isPSR = true, isChargeInAddition = true, wasAnotherPensionScheme = false)
    )
  }

  "CheckYourAnswers Controller for PSR if isPSR is true, isChargeInAddition is true and wasAnotherPensionScheme is true" must {

    behave like cyaController(
      httpPath = httpGETRoute,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate(isPSR = true, isChargeInAddition = true, wasAnotherPensionScheme = true),
      userAnswers = updateUserAnswers(ua, isPSR = true, isChargeInAddition = true, wasAnotherPensionScheme = true)
    )
  }
}
