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

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}

import data.SampleData
import models.ChargeType._
import models.{ChargeType, NormalMode, Quarter, UserAnswers}
import org.scalatest.prop.TableFor3
import pages._
import play.api.mvc.Call

class ChargeNavigatorSpec extends NavigatorBehaviour {

  private val navigator: CompoundNavigator = injector.instanceOf[CompoundNavigator]
  private val srn = "test-srn"

  val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  private def optUA(ct:ChargeType):Option[UserAnswers] = SampleData.userAnswersWithSchemeName.set(ChargeTypePage, ct).toOption
  private def chargeEMemberExists: Option[UserAnswers] = SampleData.chargeEMember.set(ChargeTypePage, ChargeTypeAnnualAllowance).toOption
  private def chargeDMemberExists: Option[UserAnswers] = SampleData.chargeDMember.set(ChargeTypePage, ChargeTypeLifetimeAllowance).toOption
  private def chargeGMemberExists: Option[UserAnswers] = SampleData.chargeGMember.set(ChargeTypePage, ChargeTypeOverseasTransfer).toOption
  private def dataWithSubmissionEnabled: Option[UserAnswers] = UserAnswers().set(QuarterPage,
    Quarter(dateTimeFormatter.format(LocalDateTime.now().minusMonths(3)), dateTimeFormatter.format(LocalDateTime.now().minusDays(1)))).toOption
  private def dataWithSubmissionNotEnabled: Option[UserAnswers] = UserAnswers().set(QuarterPage,
    Quarter(dateTimeFormatter.format(LocalDateTime.now().minusMonths(2)), dateTimeFormatter.format(LocalDateTime.now().plusDays(28)))).toOption

  "NormalMode" must {
    def normalModeRoutes: TableFor3[Page, UserAnswers, Call] =
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(ChargeTypePage)(controllers.chargeA.routes.WhatYouWillNeedController.onPageLoad(srn), optUA(ChargeTypeShortService)),
        row(ChargeTypePage)(controllers.chargeB.routes.WhatYouWillNeedController.onPageLoad(srn), optUA(ChargeTypeLumpSumDeath)),
        row(ChargeTypePage)(controllers.chargeC.routes.WhatYouWillNeedController.onPageLoad(srn), optUA(ChargeTypeAuthSurplus)),
        row(ChargeTypePage)(controllers.chargeE.routes.WhatYouWillNeedController.onPageLoad(srn), optUA(ChargeTypeAnnualAllowance)),
        row(ChargeTypePage)(controllers.chargeE.routes.MemberDetailsController.onPageLoad(NormalMode, srn, 1), chargeEMemberExists),
        row(ChargeTypePage)(controllers.chargeF.routes.WhatYouWillNeedController.onPageLoad(srn), optUA(ChargeTypeDeRegistration)),
        row(ChargeTypePage)(controllers.chargeD.routes.WhatYouWillNeedController.onPageLoad(srn), optUA(ChargeTypeLifetimeAllowance)),
        row(ChargeTypePage)(controllers.chargeD.routes.MemberDetailsController.onPageLoad(NormalMode, srn, 1), chargeDMemberExists),
        row(ChargeTypePage)(controllers.chargeG.routes.WhatYouWillNeedController.onPageLoad(srn), optUA(ChargeTypeOverseasTransfer)),
        row(ChargeTypePage)(controllers.chargeG.routes.MemberDetailsController.onPageLoad(NormalMode, srn, 1), chargeGMemberExists),
        row(AFTSummaryPage)(controllers.routes.ConfirmSubmitAFTReturnController.onPageLoad(NormalMode, srn), dataWithSubmissionEnabled),
        row(AFTSummaryPage)(controllers.routes.ChargeTypeController.onPageLoad(NormalMode, srn), dataWithSubmissionNotEnabled),
        row(AFTSummaryPage)(controllers.routes.SessionExpiredController.onPageLoad()),
        row(ConfirmSubmitAFTReturnPage)(controllers.routes.DeclarationController.onPageLoad(srn)),
        row(DeclarationPage)(controllers.routes.SessionExpiredController.onPageLoad())
      )

    behave like navigatorWithRoutesForMode(NormalMode)(navigator, normalModeRoutes, srn)
  }
}
