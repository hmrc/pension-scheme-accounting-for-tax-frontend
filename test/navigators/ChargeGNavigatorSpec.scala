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

package navigators

import controllers.chargeG.routes._
import data.SampleData
import models.{CheckMode, NormalMode, UserAnswers}
import org.scalatest.prop.TableFor3
import pages.Page
import pages.chargeG._
import play.api.mvc.Call

class ChargeGNavigatorSpec extends NavigatorBehaviour {

  import ChargeGNavigatorSpec._

  private val navigator: CompoundNavigator = injector.instanceOf[CompoundNavigator]

  "NormalMode" must {
    def normalModeRoutes: TableFor3[Page, UserAnswers, Call] =
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(WhatYouWillNeedPage)(MemberDetailsController.onPageLoad(NormalMode, srn, index)),
        row(MemberDetailsPage(index))(ChargeDetailsController.onPageLoad(NormalMode, srn, index)),
        row(ChargeDetailsPage(index))(ChargeAmountsController.onPageLoad(NormalMode, srn, index)),
        row(ChargeAmountsPage(index))(CheckYourAnswersController.onPageLoad(srn, index)),
        row(AddMembersPage)(MemberDetailsController.onPageLoad(NormalMode, srn, index), addMembersYes),
        row(AddMembersPage)(controllers.routes.AFTSummaryController.onPageLoad(srn, None), addMembersNo),
        row(DeleteMemberPage)(controllers.routes.AFTSummaryController.onPageLoad( srn, None)),
        row(DeleteMemberPage)(AddMembersController.onPageLoad(srn), Some(SampleData.chargeGMember))
      )

    behave like navigatorWithRoutesForMode(NormalMode)(navigator, normalModeRoutes, srn)
  }

  "CheckMode" must {
    def checkModeRoutes: TableFor3[Page, UserAnswers, Call] =
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(MemberDetailsPage(index))(CheckYourAnswersController.onPageLoad(srn, index)),
        row(ChargeDetailsPage(index))(CheckYourAnswersController.onPageLoad(srn, index)),
        row(ChargeAmountsPage(index))(CheckYourAnswersController.onPageLoad(srn, index))
      )

    behave like navigatorWithRoutesForMode(CheckMode)(navigator, checkModeRoutes, srn)
  }

}

object ChargeGNavigatorSpec {
  private val srn = "test-srn"
  private val index = 0
  private val addMembersYes = UserAnswers().set(AddMembersPage, true).toOption
  private val addMembersNo = UserAnswers().set(AddMembersPage, false).toOption
}
