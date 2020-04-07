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

import data.SampleData
import models.ChargeType._
import models.LocalDateBinder._
import models.{ChargeType, NormalMode, UserAnswers}
import org.scalatest.prop.TableFor3
import pages._
import play.api.mvc.Call
import utils.AFTConstants.QUARTER_START_DATE

class ChargeNavigatorSpec extends NavigatorBehaviour {

  private val navigator: CompoundNavigator = injector.instanceOf[CompoundNavigator]
  private val srn = "test-srn"
  private val startDate = QUARTER_START_DATE

  private def optUA(ct:ChargeType):Option[UserAnswers] = SampleData.userAnswersWithSchemeNamePstrQuarter.set(ChargeTypePage, ct).toOption
  private def chargeEMemberExists: Option[UserAnswers] = SampleData.chargeEMember.set(ChargeTypePage, ChargeTypeAnnualAllowance).toOption
  private def chargeDMemberExists: Option[UserAnswers] = SampleData.chargeDMember.set(ChargeTypePage, ChargeTypeLifetimeAllowance).toOption
  private def chargeGMemberExists: Option[UserAnswers] = SampleData.chargeGMember.set(ChargeTypePage, ChargeTypeOverseasTransfer).toOption
  private def aftSummaryYes: Option[UserAnswers] = UserAnswers().set(AFTSummaryPage, true).toOption
  private def aftSummaryNo: Option[UserAnswers] = UserAnswers().set(AFTSummaryPage, false).toOption

  "NormalMode" must {
    def normalModeRoutes: TableFor3[Page, UserAnswers, Call] =
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(ChargeTypePage)(controllers.chargeA.routes.WhatYouWillNeedController.onPageLoad(srn, startDate), optUA(ChargeTypeShortService)),
        row(ChargeTypePage)(controllers.chargeB.routes.WhatYouWillNeedController.onPageLoad(srn, startDate), optUA(ChargeTypeLumpSumDeath)),
        row(ChargeTypePage)(controllers.chargeC.routes.WhatYouWillNeedController.onPageLoad(srn, startDate), optUA(ChargeTypeAuthSurplus)),
        row(ChargeTypePage)(controllers.chargeE.routes.WhatYouWillNeedController.onPageLoad(srn, startDate), optUA(ChargeTypeAnnualAllowance)),
        row(ChargeTypePage)(controllers.chargeE.routes.MemberDetailsController.onPageLoad(NormalMode,srn, startDate, 1), chargeEMemberExists),
        row(ChargeTypePage)(controllers.chargeF.routes.WhatYouWillNeedController.onPageLoad(srn, startDate), optUA(ChargeTypeDeRegistration)),
        row(ChargeTypePage)(controllers.chargeD.routes.WhatYouWillNeedController.onPageLoad(srn, startDate), optUA(ChargeTypeLifetimeAllowance)),
        row(ChargeTypePage)(controllers.chargeD.routes.MemberDetailsController.onPageLoad(NormalMode,srn, startDate, 1), chargeDMemberExists),
        row(ChargeTypePage)(controllers.chargeG.routes.WhatYouWillNeedController.onPageLoad(srn, startDate), optUA(ChargeTypeOverseasTransfer)),
        row(ChargeTypePage)(controllers.chargeG.routes.MemberDetailsController.onPageLoad(NormalMode,srn, startDate, 1), chargeGMemberExists),
        row(ChargeTypePage)(controllers.routes.SessionExpiredController.onPageLoad()),
        row(AFTSummaryPage)(controllers.routes.ConfirmSubmitAFTReturnController.onPageLoad(NormalMode, srn, startDate), aftSummaryNo),
        row(AFTSummaryPage)(controllers.routes.ChargeTypeController.onPageLoad(srn, startDate), aftSummaryYes),
        row(AFTSummaryPage)(controllers.routes.SessionExpiredController.onPageLoad()),
        row(ConfirmSubmitAFTReturnPage)(controllers.routes.DeclarationController.onPageLoad(srn, startDate)),
        row(DeclarationPage)(controllers.routes.ConfirmationController.onPageLoad(srn, startDate))
      )

    behave like navigatorWithRoutesForMode(NormalMode)(navigator, normalModeRoutes,srn, startDate)
  }
}
