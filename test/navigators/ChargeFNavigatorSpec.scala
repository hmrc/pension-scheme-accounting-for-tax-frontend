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

import models.{CheckMode, NormalMode, UserAnswers}
import org.scalatest.prop.TableFor3
import pages.{Page, VersionQuery}
import pages.chargeF.{ChargeDetailsPage, CheckYourAnswersPage, WhatYouWillNeedPage}
import play.api.mvc.Call
import controllers.chargeF.routes.{ChargeDetailsController, CheckYourAnswersController}
import controllers.routes.AFTSummaryController
import play.api.libs.json.Json

class ChargeFNavigatorSpec extends NavigatorBehaviour {
  import ChargeFNavigatorSpec._

  private val navigator: CompoundNavigator = injector.instanceOf[CompoundNavigator]

  "NormalMode" must {
    def normalModeRoutes: TableFor3[Page, UserAnswers, Call] =
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(WhatYouWillNeedPage)(ChargeDetailsController.onPageLoad(NormalMode, srn)),
        row(ChargeDetailsPage)(CheckYourAnswersController.onPageLoad(srn)),
        row(CheckYourAnswersPage)(AFTSummaryController.onPageLoad(NormalMode, srn, None)),
        row(CheckYourAnswersPage)(AFTSummaryController.onPageLoad(NormalMode, srn, Some(version)), Some(uaWithVersion))
      )

    behave like navigatorWithRoutesForMode(NormalMode)(navigator, normalModeRoutes, srn)
  }

  "CheckMode" must {
    def checkModeRoutes: TableFor3[Page, UserAnswers, Call] =
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(ChargeDetailsPage)(CheckYourAnswersController.onPageLoad(srn))
      )

    behave like navigatorWithRoutesForMode(CheckMode)(navigator, checkModeRoutes, srn)
  }

}

object ChargeFNavigatorSpec {
  private val srn = "test-srn"
  private val version = "1"
  private val uaWithVersion = UserAnswers(
    Json.obj(
      VersionQuery.toString -> version
    )
  )
}
