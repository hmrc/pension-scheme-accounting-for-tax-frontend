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

package navigators

import config.FrontendAppConfig
import controllers.chargeE.routes._
import controllers.mccloud.routes._
import data.SampleData
import data.SampleData.{accessType, versionInt}
import models.ChargeType.ChargeTypeAnnualAllowance
import models.LocalDateBinder._
import models.fileUpload.InputSelection.{FileUploadInput, ManualInput}
import models.{CheckMode, NormalMode, UserAnswers, YearRange}
import org.scalatest.prop.TableFor3
import pages.chargeE._
import pages.fileUpload.InputSelectionPage
import pages.mccloud._
import pages.{Page, chargeA, chargeB}
import play.api.mvc.Call
import utils.AFTConstants.QUARTER_START_DATE

class ChargeENavigatorSpec extends NavigatorBehaviour {

  import ChargeENavigatorSpec._

  private def config: FrontendAppConfig = injector.instanceOf[FrontendAppConfig]

  private val navigator: CompoundNavigator = injector.instanceOf[CompoundNavigator]

  //scalastyle:off method.length
  "NormalMode" must {
    def normalModeRoutes: TableFor3[Page, UserAnswers, Call] =
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(WhatYouWillNeedPage)(MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt, index)),
        row(MemberDetailsPage(index))(AnnualAllowanceYearController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt, index)),
        row(AnnualAllowanceYearPage(index))(ChargeDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt, index)),
        row(ChargeDetailsPage(index))(
          IsPublicServicePensionsRemedyController
            .onPageLoad(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, versionInt, index)),
        row(IsPublicServicePensionsRemedyPage(ChargeTypeAnnualAllowance, index))(
          IsChargeInAdditionReportedController
            .onPageLoad(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, versionInt, index),
          Some(publicPensionRemedyYes)
        ),
        row(IsChargeInAdditionReportedPage(ChargeTypeAnnualAllowance, index))(CheckYourAnswersController
          .onPageLoad(srn, startDate, accessType, versionInt, index),
          Some(chargeInAdditionReportedNo)),
        row(WasAnotherPensionSchemePage(ChargeTypeAnnualAllowance, index))(
          EnterPstrController
            .onPageLoad(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, versionInt, index, schemeIndex),
          wasAnother),
        row(EnterPstrPage(ChargeTypeAnnualAllowance, index, schemeIndex))(
          TaxYearReportedAndPaidController
            .onPageLoad(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, versionInt, index, Some(schemeIndex))),
        row(ChargeAmountReportedPage(ChargeTypeAnnualAllowance, index, Some(schemeIndex)))(
          AddAnotherPensionSchemeController
            .onPageLoad(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, versionInt, index, schemeIndex),
          enterPSTRValue
        ),
        row(ChargeAmountReportedPage(ChargeTypeAnnualAllowance, index, None))(
          CheckYourAnswersController
            .onPageLoad(srn, startDate, accessType, versionInt, index)),
        row(AddAnotherPensionSchemePage(ChargeTypeAnnualAllowance, index, schemeIndex))(
          EnterPstrController
            .onPageLoad(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, versionInt, index, 1),
          isAnotherSchemeYes),
        row(AddAnotherPensionSchemePage(ChargeTypeAnnualAllowance, index, schemeIndex))(CheckYourAnswersController
          .onPageLoad(srn, startDate, accessType, versionInt, index),
          isAnotherSchemeNo),
        row(CheckYourAnswersPage)(AddMembersController.onPageLoad(srn, startDate, accessType, versionInt)),
        row(AddMembersPage)(MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt, index), addMembersYes),
        row(AddMembersPage)(controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, versionInt), addMembersNo),
        row(DeleteMemberPage)(Call("GET", config.managePensionsSchemeSummaryUrl.format(srn)), zeroedCharge),
        row(DeleteMemberPage)(controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, versionInt), multipleCharges),
        row(DeleteMemberPage)(AddMembersController.onPageLoad(srn, startDate, accessType, versionInt), Some(SampleData.chargeEMember)),
        row(InputSelectionPage(ChargeTypeAnnualAllowance))(controllers.chargeE.routes.WhatYouWillNeedController
          .onPageLoad(srn, startDate, accessType, versionInt),
          Some(manualInput)),
        row(InputSelectionPage(ChargeTypeAnnualAllowance))(
          controllers.fileUpload.routes.WhatYouWillNeedController
            .onPageLoad(srn, startDate, accessType, versionInt, ChargeTypeAnnualAllowance),
          Some(fileUploadInput)
        )
      )

    behave like navigatorWithRoutesForMode(NormalMode)(navigator, normalModeRoutes, srn, startDate, accessType, versionInt)
  }

  "CheckMode" must {
    def checkModeRoutes: TableFor3[Page, UserAnswers, Call] =
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(MemberDetailsPage(index))(CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt, index)),
        row(AnnualAllowanceYearPage(index))(CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt, index)),
        row(ChargeDetailsPage(index))(CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt, index)),
        row(IsPublicServicePensionsRemedyPage(ChargeTypeAnnualAllowance, index))(
          IsChargeInAdditionReportedController
            .onPageLoad(ChargeTypeAnnualAllowance, CheckMode, srn, startDate, accessType, versionInt, index),
          Some(publicPensionRemedyYes)),
        row(IsPublicServicePensionsRemedyPage(ChargeTypeAnnualAllowance, index))(CheckYourAnswersController
          .onPageLoad(srn, startDate, accessType, versionInt, index),
          Some(publicPensionRemedyNo)),
        row(IsChargeInAdditionReportedPage(ChargeTypeAnnualAllowance, index))(
          WasAnotherPensionSchemeController
            .onPageLoad(ChargeTypeAnnualAllowance, CheckMode, srn, startDate, accessType, versionInt, index),
          Some(chargeInAdditionReportedYes)),
        row(IsChargeInAdditionReportedPage(ChargeTypeAnnualAllowance, index))(CheckYourAnswersController
          .onPageLoad(srn, startDate, accessType, versionInt, index),
          Some(chargeInAdditionReportedNo)),
        row(WasAnotherPensionSchemePage(ChargeTypeAnnualAllowance, index))(
          EnterPstrController
            .onPageLoad(ChargeTypeAnnualAllowance, CheckMode, srn, startDate, accessType, versionInt, index, schemeIndex),
          Some(wasAnotherPensionSchemeYes)),
        row(WasAnotherPensionSchemePage(ChargeTypeAnnualAllowance, index))(TaxYearReportedAndPaidController
          .onPageLoad(ChargeTypeAnnualAllowance, CheckMode, srn, startDate, accessType, versionInt, index, None),
          Some(wasAnotherPensionSchemeNo)),
        row(EnterPstrPage(ChargeTypeAnnualAllowance, index, schemeIndex))(
          CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt, index), Some(taxYearReported)),
        row(EnterPstrPage(ChargeTypeAnnualAllowance, index, schemeIndex))(TaxYearReportedAndPaidController
          .onPageLoad(ChargeTypeAnnualAllowance, CheckMode, srn, startDate, accessType, versionInt, index, Some(schemeIndex))),
        row(TaxYearReportedAndPaidPage(ChargeTypeAnnualAllowance, index, Some(schemeIndex)))(
          TaxQuarterReportedAndPaidController
            .onPageLoad(ChargeTypeAnnualAllowance, CheckMode, srn, startDate, accessType, versionInt, index, Some(schemeIndex))),
        row(TaxQuarterReportedAndPaidPage(ChargeTypeAnnualAllowance, index, Some(schemeIndex)))(
          CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt, index), Some(chargeAmount)),
        row(TaxQuarterReportedAndPaidPage(ChargeTypeAnnualAllowance, index, Some(schemeIndex)))(ChargeAmountReportedController
          .onPageLoad(ChargeTypeAnnualAllowance, CheckMode, srn, startDate, accessType, versionInt, index, Some(schemeIndex))),
        row(ChargeAmountReportedPage(ChargeTypeAnnualAllowance, index, Some(schemeIndex)))(
          CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt, index)),
        row(ChargeAmountReportedPage(ChargeTypeAnnualAllowance, index, None))(
          CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt, index)),
        row(AddAnotherPensionSchemePage(ChargeTypeAnnualAllowance, index, schemeIndex))(
          EnterPstrController
            .onPageLoad(ChargeTypeAnnualAllowance, CheckMode, srn, startDate, accessType, versionInt, index, schemeIndex = 1),
          isAnotherSchemeYes),
        row(AddAnotherPensionSchemePage(ChargeTypeAnnualAllowance, index, schemeIndex))(CheckYourAnswersController
          .onPageLoad(srn, startDate, accessType, versionInt, index),
          isAnotherSchemeNo),
        row (RemovePensionSchemePage(ChargeTypeAnnualAllowance, index, schemeIndex))(WasAnotherPensionSchemeController
          .onPageLoad(ChargeTypeAnnualAllowance, CheckMode, srn, startDate, accessType, versionInt, index), Some(SampleData.uaWithPSPRAndOneSchemeAnnualNav)),
        row (RemovePensionSchemePage(ChargeTypeAnnualAllowance, index, schemeIndex))(CheckYourAnswersController
          .onPageLoad(srn, startDate, accessType, versionInt, index), Some(SampleData.uaWithPSPRAndTwoSchemesAnnualNav)),
        row(RemovePensionSchemePage(ChargeTypeAnnualAllowance, index, schemeIndex))(CheckYourAnswersController
          .onPageLoad(srn, startDate, accessType, versionInt, index), removeSchemeNo)
      )

    behave like navigatorWithRoutesForMode(CheckMode)(navigator, checkModeRoutes, srn, startDate, accessType, versionInt)
  }
}

object ChargeENavigatorSpec {
  private val srn = "test-srn"
  private val startDate = QUARTER_START_DATE
  private val index = 0
  private val schemeIndex = 0
  private val addMembersYes = UserAnswers().set(AddMembersPage, true).toOption
  private val addMembersNo = UserAnswers().set(AddMembersPage, false).toOption
  private val wasAnother = UserAnswers().set(WasAnotherPensionSchemePage(ChargeTypeAnnualAllowance, 0), true).toOption
  private val zeroedCharge =
    UserAnswers().set(chargeA.ChargeDetailsPage, SampleData.chargeAChargeDetails.copy(totalAmount = BigDecimal(0.00))).toOption
  private val multipleCharges = UserAnswers()
    .set(chargeA.ChargeDetailsPage, SampleData.chargeAChargeDetails)
    .flatMap(_.set(chargeB.ChargeBDetailsPage, SampleData.chargeBDetails))
    .toOption
  private val manualInput = UserAnswers().setOrException(InputSelectionPage(ChargeTypeAnnualAllowance), ManualInput)
  private val fileUploadInput = UserAnswers().setOrException(InputSelectionPage(ChargeTypeAnnualAllowance), FileUploadInput)
  private val publicPensionRemedyYes = UserAnswers().setOrException(IsPublicServicePensionsRemedyPage(ChargeTypeAnnualAllowance, 0), true)
  private val publicPensionRemedyNo = UserAnswers().setOrException(IsPublicServicePensionsRemedyPage(ChargeTypeAnnualAllowance, 0), false)
  private val chargeInAdditionReportedYes = UserAnswers().setOrException(IsChargeInAdditionReportedPage(ChargeTypeAnnualAllowance, 0), true)
  private val chargeInAdditionReportedNo = UserAnswers().setOrException(IsChargeInAdditionReportedPage(ChargeTypeAnnualAllowance, 0), false)
  private val wasAnotherPensionSchemeYes = UserAnswers().setOrException(WasAnotherPensionSchemePage(ChargeTypeAnnualAllowance, 0), true)
  private val wasAnotherPensionSchemeNo = UserAnswers().setOrException(WasAnotherPensionSchemePage(ChargeTypeAnnualAllowance, 0), false)
  private val taxYearReported = UserAnswers().setOrException(TaxYearReportedAndPaidPage(ChargeTypeAnnualAllowance, 0, Some(0)), YearRange("2019"))
  private val chargeAmount = UserAnswers().setOrException(ChargeAmountReportedPage(ChargeTypeAnnualAllowance, 0, Some(0)), SampleData.chargeAmountReported)
  private val isAnotherSchemeYes = UserAnswers().set(AddAnotherPensionSchemePage(ChargeTypeAnnualAllowance, 0, 0), true).toOption
  private val isAnotherSchemeNo = UserAnswers().set(AddAnotherPensionSchemePage(ChargeTypeAnnualAllowance, 0, 0), false).toOption
  private val enterPSTRValue = UserAnswers().set(EnterPstrPage(ChargeTypeAnnualAllowance, 0, 0), "20123456RQ").toOption
  private val removeSchemeNo = UserAnswers().set(RemovePensionSchemePage(ChargeTypeAnnualAllowance, 0, 0), false).toOption
}
