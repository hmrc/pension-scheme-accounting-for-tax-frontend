/*
 * Copyright 2019 HM Revenue & Customs
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

import data.SampleData
import models.{ChargeType, NormalMode, UserAnswers}
import org.scalatest.prop.TableFor3
import pages.{ChargeTypePage, Page}
import play.api.mvc.Call

class ChargeNavigatorSpec extends NavigatorBehaviour {

  private val navigator: CompoundNavigator = injector.instanceOf[CompoundNavigator]
  private val srn = "test-srn"

  private def optUA(ct:ChargeType):Option[UserAnswers] = SampleData.userAnswersWithSchemeName.set(ChargeTypePage, ct).toOption

  "NormalMode" must {
    def normalModeRoutes: TableFor3[Page, UserAnswers, Call] =
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(ChargeTypePage)(controllers.chargeA.routes.WhatYouWillNeedController.onPageLoad(srn), optUA(ChargeType.ChargeTypeShortService)),
        row(ChargeTypePage)(controllers.chargeB.routes.WhatYouWillNeedController.onPageLoad(srn), optUA(ChargeType.ChargeTypeLumpSumDeath)),
        row(ChargeTypePage)(controllers.chargeE.routes.WhatYouWillNeedController.onPageLoad(srn), optUA(ChargeType.ChargeTypeAnnualAllowance)),
        row(ChargeTypePage)(controllers.chargeF.routes.WhatYouWillNeedController.onPageLoad(srn), optUA(ChargeType.ChargeTypeDeRegistration))
      )

    behave like navigatorWithRoutesForMode(NormalMode)(navigator, normalModeRoutes, srn)
  }
}
