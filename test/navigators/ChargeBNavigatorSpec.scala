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

import controllers.chargeB.routes.{ChargeDetailsController, CheckYourAnswersController}
import controllers.routes.AFTSummaryController
import models.{CheckMode, NormalMode, UserAnswers}
import org.scalatest.prop.TableFor3
import pages.chargeB.{ChargeBDetailsPage, CheckYourAnswersPage, WhatYouWillNeedPage}
import pages.{Page, VersionQuery}
import play.api.libs.json.Json
import play.api.mvc.Call

class ChargeBNavigatorSpec extends NavigatorBehaviour {
  import ChargeBNavigatorSpec._
  private val navigator: CompoundNavigator = injector.instanceOf[CompoundNavigator]
  private val srn = "test-srn"

  "NormalMode" must {
    def normalModeRoutes: TableFor3[Page, UserAnswers, Call] =
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(WhatYouWillNeedPage)(ChargeDetailsController.onPageLoad(NormalMode, srn)),
        row(ChargeBDetailsPage)(CheckYourAnswersController.onPageLoad(srn)),
        row(CheckYourAnswersPage)(AFTSummaryController.onPageLoad(NormalMode, srn, None)),
        row(CheckYourAnswersPage)(AFTSummaryController.onPageLoad(NormalMode, srn, Some(version)), Some(uaWithVersion))
      )

    behave like navigatorWithRoutesForMode(NormalMode)(navigator, normalModeRoutes, srn)
  }

  "CheckMode" must {
    def checkModeRoutes: TableFor3[Page, UserAnswers, Call] =
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(ChargeBDetailsPage)(CheckYourAnswersController.onPageLoad(srn))
      )

    behave like navigatorWithRoutesForMode(CheckMode)(navigator, checkModeRoutes, srn)
  }

}

object ChargeBNavigatorSpec {
  private val version = "1"
  private val uaWithVersion = UserAnswers(
    Json.obj(
      VersionQuery.toString -> version
    )
  )
}
