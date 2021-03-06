/*
 * Copyright 2021 HM Revenue & Customs
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
import data.SampleData
import data.SampleData.{versionInt, accessType}
import models.LocalDateBinder._
import models.{NormalMode, CheckMode, UserAnswers}
import org.scalatest.prop.TableFor3
import pages.{Page, chargeA, chargeB}
import pages.chargeE._
import play.api.mvc.Call
import utils.AFTConstants.QUARTER_START_DATE

class ChargeENavigatorSpec extends NavigatorBehaviour {

  import ChargeENavigatorSpec._
  private def config: FrontendAppConfig = injector.instanceOf[FrontendAppConfig]
  private val navigator: CompoundNavigator = injector.instanceOf[CompoundNavigator]

  "NormalMode" must {
    def normalModeRoutes: TableFor3[Page, UserAnswers, Call] =
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(WhatYouWillNeedPage)(MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt, index)),
        row(MemberDetailsPage(index))(AnnualAllowanceYearController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt, index)),
        row(AnnualAllowanceYearPage(index))(ChargeDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt, index)),
        row(ChargeDetailsPage(index))(CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt, index)),
        row(CheckYourAnswersPage)(AddMembersController.onPageLoad(srn, startDate, accessType, versionInt)),
        row(AddMembersPage)(MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt, index), addMembersYes),
        row(AddMembersPage)(controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, versionInt), addMembersNo),
        row(DeleteMemberPage)(Call("GET", config.managePensionsSchemeSummaryUrl.format(srn)), zeroedCharge),
        row(DeleteMemberPage)(controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, versionInt), multipleCharges),
        row(DeleteMemberPage)(AddMembersController.onPageLoad(srn, startDate, accessType, versionInt), Some(SampleData.chargeEMember))
      )

    behave like navigatorWithRoutesForMode(NormalMode)(navigator, normalModeRoutes, srn, startDate, accessType, versionInt)
  }

  "CheckMode" must {
    def checkModeRoutes: TableFor3[Page, UserAnswers, Call] =
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(MemberDetailsPage(index))(CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt, index)),
        row(AnnualAllowanceYearPage(index))(CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt, index)),
        row(ChargeDetailsPage(index))(CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt, index))
      )

    behave like navigatorWithRoutesForMode(CheckMode)(navigator, checkModeRoutes, srn, startDate, accessType, versionInt)
  }

}

object ChargeENavigatorSpec {
  private val srn = "test-srn"
  private val startDate = QUARTER_START_DATE
  private val index = 0
  private val addMembersYes = UserAnswers().set(AddMembersPage, true).toOption
  private val addMembersNo = UserAnswers().set(AddMembersPage, false).toOption
  private val zeroedCharge = UserAnswers().set(chargeA.ChargeDetailsPage,
    SampleData.chargeAChargeDetails.copy(totalAmount = BigDecimal(0.00))).toOption
  private val multipleCharges = UserAnswers().set(chargeA.ChargeDetailsPage, SampleData.chargeAChargeDetails)
    .flatMap(_.set(chargeB.ChargeBDetailsPage, SampleData.chargeBDetails)).toOption
}
