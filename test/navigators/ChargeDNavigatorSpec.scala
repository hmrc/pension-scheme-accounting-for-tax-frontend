/*
 * Copyright 2022 HM Revenue & Customs
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
import controllers.chargeD.routes._
import controllers.mccloud.routes._
import data.SampleData
import data.SampleData.{accessType, versionInt}
import models.ChargeType.ChargeTypeLifetimeAllowance
import models.LocalDateBinder._
import models.fileUpload.InputSelection.{FileUploadInput, ManualInput}
import models.{CheckMode, NormalMode, UserAnswers}
import org.scalatest.prop.TableFor3
import pages.chargeD._
import pages.fileUpload.InputSelectionPage
import pages.mccloud._
import pages.{Page, chargeA, chargeB}
import play.api.mvc.Call
import utils.AFTConstants.QUARTER_START_DATE

class ChargeDNavigatorSpec extends NavigatorBehaviour {

  import ChargeDNavigatorSpec._
  private def config: FrontendAppConfig = injector.instanceOf[FrontendAppConfig]
  private val navigator: CompoundNavigator = injector.instanceOf[CompoundNavigator]

  "NormalMode" must {
    def normalModeRoutes: TableFor3[Page, UserAnswers, Call] =
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(WhatYouWillNeedPage)(MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt, index)),
        row(MemberDetailsPage(index))(ChargeDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt, index)),
        row(ChargeDetailsPage(index))(
          IsPublicServicePensionsRemedyController
            .onPageLoad(ChargeTypeLifetimeAllowance, NormalMode, srn, startDate, accessType, versionInt, index)),
        row(IsPublicServicePensionsRemedyPage(ChargeTypeLifetimeAllowance, index))(
          IsChargeInAdditionReportedController
            .onPageLoad(ChargeTypeLifetimeAllowance, NormalMode, srn, startDate, accessType, versionInt, index),
          Some(publicPensionRemedyYes)
        ),
        row(IsChargeInAdditionReportedPage(ChargeTypeLifetimeAllowance, index))(CheckYourAnswersController
                                                                                  .onPageLoad(srn, startDate, accessType, versionInt, index),
                                                                                Some(chargeInAdditionReportedNo)),
        row(WasAnotherPensionSchemePage(ChargeTypeLifetimeAllowance, index))(
          EnterPstrController
            .onPageLoad(ChargeTypeLifetimeAllowance, NormalMode, srn, startDate, accessType, versionInt, index, schemeIndex),
          wasAnother),
        row(EnterPstrPage(ChargeTypeLifetimeAllowance, index, schemeIndex))(
          TaxYearReportedAndPaidController
            .onPageLoad(ChargeTypeLifetimeAllowance, NormalMode, srn, startDate, accessType, versionInt, index, Some(schemeIndex))),
        row(ChargeAmountReportedPage(ChargeTypeLifetimeAllowance, index, Some(schemeIndex)))(
          AddAnotherPensionSchemeController
            .onPageLoad(ChargeTypeLifetimeAllowance, NormalMode, srn, startDate, accessType, versionInt, index, schemeIndex),
          enterPSTRValue
        ),
        row(ChargeAmountReportedPage(ChargeTypeLifetimeAllowance, index, None))(
          CheckYourAnswersController
            .onPageLoad(srn, startDate, accessType, versionInt, index)),

        row(AddAnotherPensionSchemePage(ChargeTypeLifetimeAllowance, index, schemeIndex))(
          EnterPstrController
            .onPageLoad(ChargeTypeLifetimeAllowance, NormalMode, srn, startDate, accessType, versionInt, index, 1),
          isAnotherSchemeYes),
        row(AddAnotherPensionSchemePage(ChargeTypeLifetimeAllowance, index, schemeIndex))(
          CheckYourAnswersController
            .onPageLoad(srn, startDate, accessType, versionInt, index),
          isAnotherSchemeNo),
        row(CheckYourAnswersPage)(AddMembersController.onPageLoad(srn, startDate, accessType, versionInt)),
        row(AddMembersPage)(MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt, index), addMembersYes),
        row(AddMembersPage)(controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, versionInt), addMembersNo),
        row(DeleteMemberPage)(Call("GET", config.managePensionsSchemeSummaryUrl.format(srn)), zeroedCharge),
        row(DeleteMemberPage)(controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, versionInt), multipleCharges),
        row(DeleteMemberPage)(AddMembersController.onPageLoad(srn, startDate, accessType, versionInt), Some(SampleData.chargeDMember)),
        row(InputSelectionPage(ChargeTypeLifetimeAllowance))(controllers.chargeD.routes.WhatYouWillNeedController
                                                               .onPageLoad(srn, startDate, accessType, versionInt),
                                                             Some(manualInput)),
        row(InputSelectionPage(ChargeTypeLifetimeAllowance))(
          controllers.fileUpload.routes.WhatYouWillNeedController
            .onPageLoad(srn, startDate, accessType, versionInt, ChargeTypeLifetimeAllowance),
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
        row(ChargeDetailsPage(index))(CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt, index))
      )

    behave like navigatorWithRoutesForMode(CheckMode)(navigator, checkModeRoutes, srn, startDate, accessType, versionInt)
  }

}

object ChargeDNavigatorSpec {
  private val srn = "test-srn"
  private val startDate = QUARTER_START_DATE
  private val index = 0
  private val schemeIndex = 0
  private val addMembersYes = UserAnswers().set(AddMembersPage, true).toOption
  private val addMembersNo = UserAnswers().set(AddMembersPage, false).toOption
  private val wasAnother = UserAnswers().set(WasAnotherPensionSchemePage(ChargeTypeLifetimeAllowance, 0), true).toOption
  private val zeroedCharge =
    UserAnswers().set(chargeA.ChargeDetailsPage, SampleData.chargeAChargeDetails.copy(totalAmount = BigDecimal(0.00))).toOption
  private val multipleCharges = UserAnswers()
    .set(chargeA.ChargeDetailsPage, SampleData.chargeAChargeDetails)
    .flatMap(_.set(chargeB.ChargeBDetailsPage, SampleData.chargeBDetails))
    .toOption
  private val manualInput = UserAnswers().setOrException(InputSelectionPage(ChargeTypeLifetimeAllowance), ManualInput)
  private val fileUploadInput = UserAnswers().setOrException(InputSelectionPage(ChargeTypeLifetimeAllowance), FileUploadInput)
  private val publicPensionRemedyYes = UserAnswers().setOrException(IsPublicServicePensionsRemedyPage(ChargeTypeLifetimeAllowance, 0), true)
  private val chargeInAdditionReportedNo = UserAnswers().setOrException(IsChargeInAdditionReportedPage(ChargeTypeLifetimeAllowance, 0), false)

  private val isAnotherSchemeYes = UserAnswers().set(AddAnotherPensionSchemePage(ChargeTypeLifetimeAllowance, 0, 0), true).toOption
  private val isAnotherSchemeNo = UserAnswers().set(AddAnotherPensionSchemePage(ChargeTypeLifetimeAllowance, 0, 0), false).toOption
  private val enterPSTRValue = UserAnswers().set(EnterPstrPage(ChargeTypeLifetimeAllowance, 0, 0), "20123456RQ").toOption
}
