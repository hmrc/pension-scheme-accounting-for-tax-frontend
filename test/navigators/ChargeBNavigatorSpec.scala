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

package navigators

import controllers.chargeB.routes.{ChargeDetailsController, CheckYourAnswersController}
import controllers.routes.AFTSummaryController
import data.SampleData.{accessType, versionInt}
import models.LocalDateBinder._
import models.{CheckMode, NormalMode, UserAnswers}
import org.scalatest.prop.TableFor3
import pages.Page
import pages.chargeB.{ChargeBDetailsPage, CheckYourAnswersPage, WhatYouWillNeedPage}
import play.api.mvc.Call
import utils.AFTConstants.QUARTER_START_DATE

class ChargeBNavigatorSpec extends NavigatorBehaviour {
  private val navigator: CompoundNavigator = injector.instanceOf[CompoundNavigator]
  private val srn = "test-srn"
  private val startDate = QUARTER_START_DATE

  "NormalMode" must {
    def normalModeRoutes: TableFor3[Page, UserAnswers, Call] =
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(WhatYouWillNeedPage)(ChargeDetailsController.onPageLoad(NormalMode,srn, startDate, accessType, versionInt)),
        row(ChargeBDetailsPage)(CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt)),
        row(CheckYourAnswersPage)(AFTSummaryController.onPageLoad(srn, startDate, accessType, versionInt))
      )

    behave like navigatorWithRoutesForMode(NormalMode)(navigator, normalModeRoutes,srn, startDate, accessType, versionInt)
  }

  "CheckMode" must {
    def checkModeRoutes: TableFor3[Page, UserAnswers, Call] =
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(ChargeBDetailsPage)(CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt))
      )

    behave like navigatorWithRoutesForMode(CheckMode)(navigator, checkModeRoutes, srn, startDate, accessType, versionInt)
  }

}


