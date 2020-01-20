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

import controllers.chargeD.routes._
import data.SampleData
import models.{CheckMode, NormalMode, UserAnswers}
import org.scalatest.prop.TableFor3
import pages.chargeD._
import pages.{Page, VersionQuery}
import play.api.libs.json.Json
import play.api.mvc.Call

class ChargeDNavigatorSpec extends NavigatorBehaviour {

  import ChargeDNavigatorSpec._

  private val navigator: CompoundNavigator = injector.instanceOf[CompoundNavigator]


  "NormalMode" must {
    def normalModeRoutes: TableFor3[Page, UserAnswers, Call] =
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(WhatYouWillNeedPage)(MemberDetailsController.onPageLoad(NormalMode, srn, index)),
        row(MemberDetailsPage(index))(ChargeDetailsController.onPageLoad(NormalMode, srn, index)),
        row(ChargeDetailsPage(index))(CheckYourAnswersController.onPageLoad(srn, index)),
        row(AddMembersPage)(MemberDetailsController.onPageLoad(NormalMode, srn, index), addMembersYes),
        row(AddMembersPage)(controllers.routes.AFTSummaryController.onPageLoad( srn, None), addMembersNo),
        row(AddMembersPage)(controllers.routes.AFTSummaryController.onPageLoad(srn, Some(version)), optionUAWithAddMembersNoAndVersion),
        row(DeleteMemberPage)(controllers.routes.AFTSummaryController.onPageLoad(srn, None)),
        row(DeleteMemberPage)(controllers.routes.AFTSummaryController.onPageLoad(srn, Some(version)), Some(uaWithVersion)),
        row(DeleteMemberPage)(AddMembersController.onPageLoad(srn), Some(SampleData.chargeDMember))

      )

    behave like navigatorWithRoutesForMode(NormalMode)(navigator, normalModeRoutes, srn)
  }

  "CheckMode" must {
    def checkModeRoutes: TableFor3[Page, UserAnswers, Call] =
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(MemberDetailsPage(index))(CheckYourAnswersController.onPageLoad(srn, index)),
        row(ChargeDetailsPage(index))(CheckYourAnswersController.onPageLoad(srn, index))
      )

    behave like navigatorWithRoutesForMode(CheckMode)(navigator, checkModeRoutes, srn)
  }

}

object ChargeDNavigatorSpec {
  private val srn = "test-srn"
  private val index = 0
  private val addMembersYes = UserAnswers().set(AddMembersPage, true).toOption
  private val addMembersNo = UserAnswers().set(AddMembersPage, false).toOption
  private val version = "1"
  private val optionUAWithAddMembersNoAndVersion = addMembersNo.flatMap(_.set(VersionQuery, version).toOption)
  private val uaWithVersion = UserAnswers(
    Json.obj(
      VersionQuery.toString -> version
    )
  )
}
