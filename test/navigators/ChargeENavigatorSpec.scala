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

import controllers.chargeE.routes._
import data.SampleData
import models.{CheckMode, NormalMode, UserAnswers}
import org.scalatest.prop.TableFor3
import pages.Page
import pages.chargeE.{AnnualAllowanceYearPage, ChargeDetailsPage, DeleteMemberPage, MemberDetailsPage, WhatYouWillNeedPage}
import play.api.mvc.Call

class ChargeENavigatorSpec extends NavigatorBehaviour {

  private val navigator: CompoundNavigator = injector.instanceOf[CompoundNavigator]
  private val srn = "test-srn"
  private val index = 0


  "NormalMode" must {
    def normalModeRoutes: TableFor3[Page, UserAnswers, Call] =
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(WhatYouWillNeedPage)(MemberDetailsController.onPageLoad(NormalMode, srn, index)),
        row(MemberDetailsPage(index))(AnnualAllowanceYearController.onPageLoad(NormalMode, srn, index)),
        row(AnnualAllowanceYearPage(index))(ChargeDetailsController.onPageLoad(NormalMode, srn, index)),
        row(ChargeDetailsPage(index))(CheckYourAnswersController.onPageLoad(srn, index)),
        row(DeleteMemberPage)(controllers.routes.AFTSummaryController.onPageLoad(NormalMode, srn)),
        row(DeleteMemberPage)(AddMembersController.onPageLoad(srn), Some(SampleData.chargeEMember))
      )

    behave like navigatorWithRoutesForMode(NormalMode)(navigator, normalModeRoutes, srn)
  }

  "CheckMode" must {
    def checkModeRoutes: TableFor3[Page, UserAnswers, Call] =
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(MemberDetailsPage(index))(CheckYourAnswersController.onPageLoad(srn, index)),
        row(AnnualAllowanceYearPage(index))(CheckYourAnswersController.onPageLoad(srn, index)),
        row(ChargeDetailsPage(index))(CheckYourAnswersController.onPageLoad(srn, index))
      )

    behave like navigatorWithRoutesForMode(CheckMode)(navigator, checkModeRoutes, srn)
  }

}
