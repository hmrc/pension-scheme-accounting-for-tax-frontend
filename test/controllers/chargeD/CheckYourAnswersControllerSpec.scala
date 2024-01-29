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

package controllers.chargeD

import behaviours.CheckYourAnswersBehaviour
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData._
import helpers.CYAChargeDHelper
import matchers.JsonMatchers
import models.ChargeType.ChargeTypeLifetimeAllowance
import models.LocalDateBinder._
import models.mccloud.{PensionsRemedySchemeSummary, PensionsRemedySummary}
import models.{UserAnswers, YearRange}
import org.mockito.Mockito.when
import pages.chargeD.{ChargeDetailsPage, CheckYourAnswersPage, MemberDetailsPage}
import pages.mccloud._
import pages.{IsPublicServicePensionsRemedyPage, QuarterPage}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.viewmodels.NunjucksSupport

import java.time.LocalDate
import scala.collection.Seq

class CheckYourAnswersControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with CheckYourAnswersBehaviour {
  //scalastyle:off magic.number

  private val dynamicYearRange = YearRange("2019")

  private val templateToBeRendered = "check-your-answers.njk"
  private val lifetimeAllowanceCharge = ChargeTypeLifetimeAllowance
  private val mccloudPsrAlwaysTrueStartDate = LocalDate.of(2024, 4, 1)

  private def httpGETRoute: String = controllers.chargeD.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt, 0).url

  private def httpOnClickRoute: String = controllers.chargeD.routes.CheckYourAnswersController.onClick(srn, startDate, accessType, versionInt, 0).url

  val pensionsRemedySchemeSummaryEmpty: List[Nothing] = List()

  val pensionsRemedySchemeSummaryWithPstr: List[PensionsRemedySchemeSummary] =
    List(PensionsRemedySchemeSummary(schemeIndex, Some(pstrNumber), Some(dynamicYearRange), Some(taxQuarter), Some(chargeAmountReported)))

  val pensionsRemedySchemeSummary: List[PensionsRemedySchemeSummary] =
    List(PensionsRemedySchemeSummary(schemeIndex, None, Some(dynamicYearRange), Some(taxQuarter), Some(chargeAmountReported)))

  private def ua: UserAnswers =
    userAnswersWithSchemeNamePstrQuarter
      .set(MemberDetailsPage(0), memberDetails)
      .toOption
      .get
      .set(ChargeDetailsPage(0), chargeDDetails)
      .toOption
      .get

  private val helper = new CYAChargeDHelper(srn, startDate, accessType, versionInt)

  def psprSummary(isPSR: Boolean, isChargeInAddition: Boolean, wasAnotherPensionScheme: Boolean): PensionsRemedySummary = {

    (isPSR, isChargeInAddition, wasAnotherPensionScheme) match {
      case (false, _, _) =>
        PensionsRemedySummary(Some(isPSR), Some(isChargeInAddition), Some(wasAnotherPensionScheme), pensionsRemedySchemeSummaryEmpty)
      case (true, false, _) =>
        PensionsRemedySummary(Some(isPSR), Some(isChargeInAddition), Some(wasAnotherPensionScheme), pensionsRemedySchemeSummaryEmpty)
      case (true, true, false) =>
        PensionsRemedySummary(Some(isPSR), Some(isChargeInAddition), Some(wasAnotherPensionScheme), pensionsRemedySchemeSummary)
      case (true, true, true) =>
        PensionsRemedySummary(Some(isPSR), Some(isChargeInAddition), Some(wasAnotherPensionScheme), pensionsRemedySchemeSummaryWithPstr)
    }
  }

  private def updateUserAnswers(ua: UserAnswers, isPSR: Boolean, isChargeInAddition: Boolean, wasAnotherPensionScheme: Boolean): UserAnswers = {

    (isPSR, isChargeInAddition, wasAnotherPensionScheme) match {
      case (false, _, _) => ua.setOrException(IsPublicServicePensionsRemedyPage(lifetimeAllowanceCharge, Some(0)), isPSR)
      case (true, false, _) =>
        ua.setOrException(IsPublicServicePensionsRemedyPage(lifetimeAllowanceCharge, Some(0)), isPSR)
          .setOrException(IsChargeInAdditionReportedPage(lifetimeAllowanceCharge, 0), isChargeInAddition)
      case (true, true, false) =>
        ua.setOrException(IsPublicServicePensionsRemedyPage(lifetimeAllowanceCharge, Some(0)), isPSR)
          .setOrException(IsChargeInAdditionReportedPage(lifetimeAllowanceCharge, 0), isChargeInAddition)
          .setOrException(WasAnotherPensionSchemePage(lifetimeAllowanceCharge, 0), wasAnotherPensionScheme)
          .setOrException(TaxYearReportedAndPaidPage(lifetimeAllowanceCharge, 0, None), dynamicYearRange)
          .setOrException(TaxQuarterReportedAndPaidPage(lifetimeAllowanceCharge, 0, None), taxQuarter)
          .setOrException(ChargeAmountReportedPage(lifetimeAllowanceCharge, 0, None), chargeAmountReported)
      case (true, true, true) =>
        ua.setOrException(IsPublicServicePensionsRemedyPage(lifetimeAllowanceCharge, Some(0)), isPSR)
          .setOrException(IsChargeInAdditionReportedPage(lifetimeAllowanceCharge, 0), isChargeInAddition)
          .setOrException(WasAnotherPensionSchemePage(lifetimeAllowanceCharge, 0), wasAnotherPensionScheme)
          .setOrException(EnterPstrPage(lifetimeAllowanceCharge, 0, 0), pstrNumber)
          .setOrException(TaxYearReportedAndPaidPage(lifetimeAllowanceCharge, 0, Some(0)), dynamicYearRange)
          .setOrException(TaxQuarterReportedAndPaidPage(lifetimeAllowanceCharge, 0, Some(0)), taxQuarter)
          .setOrException(ChargeAmountReportedPage(lifetimeAllowanceCharge, 0, Some(0)), chargeAmountReported)
    }
  }

  private def rows(isPsrAlwaysTrue: Boolean, isPSR: Boolean, isChargeInAddition: Boolean, wasAnotherPensionScheme: Boolean) =
    Seq(
      helper.isPsprForChargeD(isPsrAlwaysTrue, 0, Some(isPSR)),
      helper.chargeDMemberDetails(0, memberDetails),
      helper.chargeDDetails(0, chargeDDetails),
      Seq(helper.total(chargeAmount1 + chargeAmount2)),
      helper.psprChargeDetails(0, psprSummary(isPSR, isChargeInAddition, wasAnotherPensionScheme)).getOrElse(None),
      helper.psprSchemesChargeDetails(0, psprSummary(isPSR, isChargeInAddition, wasAnotherPensionScheme), wasAnotherPensionScheme)
    ).flatten

  private def jsonToPassToTemplate(isPsrAlwaysTrue: Boolean, isPSR: Boolean, isChargeInAddition: Boolean,
                                   wasAnotherPensionScheme: Boolean): JsObject = Json.obj(
    "list" -> rows(isPsrAlwaysTrue, isPSR, isChargeInAddition, wasAnotherPensionScheme)
  )

  private val rowsWithoutPSR = Seq(
    helper.chargeDMemberDetails(0, memberDetails),
    helper.chargeDDetails(0, chargeDDetails),
    Seq(helper.total(chargeAmount1 + chargeAmount2))
  ).flatten

  private val jsonToPassToTemplateNoPSR: JsObject = Json.obj(
    "list" -> rowsWithoutPSR
  )

  private def isPsrAlwaysTrue(ua: UserAnswers): Boolean = {
    when(mockAppConfig.mccloudPsrAlwaysTrueStartDate).thenReturn(mccloudPsrAlwaysTrueStartDate)
    ua.get(QuarterPage) match {
      case Some(aftQuarter) =>
        aftQuarter.startDate.isAfter(mccloudPsrAlwaysTrueStartDate) || aftQuarter.startDate.isEqual(mccloudPsrAlwaysTrueStartDate)
      case _ => false
    }
  }

  "CheckYourAnswers Controller for PSR if isPSR is false, isChargeInAddition is false and wasAnotherPensionScheme is false" must {
    val updatedUA = ua.setOrException(QuarterPage, SampleData.taxQtrAprToJun2023)
    behave like cyaController(
      httpPath = httpGETRoute,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate(isPsrAlwaysTrue(updatedUA), isPSR = false, isChargeInAddition = false, wasAnotherPensionScheme = false),
      userAnswers = updateUserAnswers(updatedUA, isPSR = false, isChargeInAddition = false, wasAnotherPensionScheme = false)
    )

    behave like redirectToErrorOn5XX(
      httpPath = httpOnClickRoute,
      page = CheckYourAnswersPage,
      userAnswers = updatedUA
    )

    "CheckYourAnswers Controller with both rates of tax set" must {
      behave like controllerWithOnClick(
        httpPath = httpOnClickRoute,
        page = CheckYourAnswersPage,
        userAnswers = updatedUA
      )
    }

    "CheckYourAnswers Controller with no 25% rate of tax set" must {
      behave like controllerWithOnClick(
        httpPath = httpOnClickRoute,
        page = CheckYourAnswersPage,
        userAnswers = updatedUA.set(ChargeDetailsPage(0), chargeDDetails.copy(taxAt25Percent = None)).get
      )
    }

    "CheckYourAnswers Controller with no 55% rate of tax set" must {
      behave like controllerWithOnClick(
        httpPath = httpOnClickRoute,
        page = CheckYourAnswersPage,
        userAnswers = updatedUA.set(ChargeDetailsPage(0), chargeDDetails.copy(taxAt55Percent = None)).get
      )
    }
  }

  "CheckYourAnswers Controller Without PSR Questions" must {
    behave like cyaController(
      httpPath = httpGETRoute,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplateNoPSR,
      userAnswers = ua
    )

    behave like redirectToErrorOn5XX(
      httpPath = httpOnClickRoute,
      page = CheckYourAnswersPage,
      userAnswers = ua
    )

    "CheckYourAnswers Controller with both rates of tax set" must {
      behave like controllerWithOnClick(
        httpPath = httpOnClickRoute,
        page = CheckYourAnswersPage,
        userAnswers = ua
      )
    }

    "CheckYourAnswers Controller with no 25% rate of tax set" must {
      behave like controllerWithOnClick(
        httpPath = httpOnClickRoute,
        page = CheckYourAnswersPage,
        userAnswers = ua.set(ChargeDetailsPage(0), chargeDDetails.copy(taxAt25Percent = None)).get
      )
    }

    "CheckYourAnswers Controller with no 55% rate of tax set" must {
      behave like controllerWithOnClick(
        httpPath = httpOnClickRoute,
        page = CheckYourAnswersPage,
        userAnswers = ua.set(ChargeDetailsPage(0), chargeDDetails.copy(taxAt55Percent = None)).get
      )
    }
  }

  "CheckYourAnswers Controller for PSR if isPSR is true, isChargeInAddition is false and wasAnotherPensionScheme is false" must {
    val updatedUA = ua.setOrException(QuarterPage, SampleData.taxQtrAprToJun2023)
    behave like cyaController(
      httpPath = httpGETRoute,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate(isPsrAlwaysTrue(updatedUA), isPSR = true, isChargeInAddition = false, wasAnotherPensionScheme = false),
      userAnswers = updateUserAnswers(updatedUA, isPSR = true, isChargeInAddition = false, wasAnotherPensionScheme = false)
    )
  }

  "CheckYourAnswers Controller for PSR if isPSR is true, isChargeInAddition is true and wasAnotherPensionScheme is false" must {
    val updatedUA = ua.setOrException(QuarterPage, SampleData.taxQtrAprToJun2023)

    behave like cyaController(
      httpPath = httpGETRoute,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate(isPsrAlwaysTrue(updatedUA), isPSR = true, isChargeInAddition = true, wasAnotherPensionScheme = false),
      userAnswers = updateUserAnswers(updatedUA, isPSR = true, isChargeInAddition = true, wasAnotherPensionScheme = false)
    )
  }

  "CheckYourAnswers Controller for PSR if isPSR is true, isChargeInAddition is true and wasAnotherPensionScheme is true" must {
    val updatedUA = ua.setOrException(QuarterPage, SampleData.taxQtrAprToJun2023)
    behave like cyaController(
      httpPath = httpGETRoute,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate(isPsrAlwaysTrue(updatedUA), isPSR = true, isChargeInAddition = true, wasAnotherPensionScheme = true),
      userAnswers = updateUserAnswers(updatedUA, isPSR = true, isChargeInAddition = true, wasAnotherPensionScheme = true)
    )
  }
}
