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

import java.time.LocalDate

import config.FrontendAppConfig
import data.SampleData
import data.SampleData.{versionInt, accessType}
import models.ChargeType._
import models.LocalDateBinder._
import models.{Quarter, NormalMode, ChargeType, UserAnswers}
import org.scalatest.prop.{TableFor5, TableFor3}
import pages._
import play.api.mvc.Call
import utils.AFTConstants._

class ChargeNavigatorSpec extends NavigatorBehaviour {

  private def config: FrontendAppConfig = injector.instanceOf[FrontendAppConfig]
  private val navigator: CompoundNavigator = injector.instanceOf[CompoundNavigator]
  private val srn = "test-srn"
  private val startDate = QUARTER_START_DATE

  private def optUA(ct:ChargeType):Option[UserAnswers] = SampleData.userAnswersWithSchemeNamePstrQuarter.set(ChargeTypePage, ct).toOption
  private def chargeEMemberExists: Option[UserAnswers] = SampleData.chargeEMember.set(ChargeTypePage, ChargeTypeAnnualAllowance).toOption
  private def chargeDMemberExists: Option[UserAnswers] = SampleData.chargeDMember.set(ChargeTypePage, ChargeTypeLifetimeAllowance).toOption
  private def chargeGMemberExists: Option[UserAnswers] = SampleData.chargeGMember.set(ChargeTypePage, ChargeTypeOverseasTransfer).toOption
  private def aftSummaryYes: Option[UserAnswers] = UserAnswers().set(AFTSummaryPage, true).toOption
  private def aftSummaryNo(quarter:Quarter):Option[UserAnswers] =
    Option(
      UserAnswers()
        .setOrException(AFTSummaryPage, false)
        .setOrException(QuarterPage, quarter)
    )

  private def confirmSubmitAFTReturn(confirmSubmit:Boolean):Option[UserAnswers] =
    Option(UserAnswers().setOrException(ConfirmSubmitAFTReturnPage, confirmSubmit))

  private def confirmAmendAFTReturn(confirmAmend:Boolean):Option[UserAnswers] =
    Option(UserAnswers().setOrException(ConfirmSubmitAFTAmendmentPage, confirmAmend))

  "NormalMode" must {
    def normalModeRoutes: TableFor3[Page, UserAnswers, Call] = {
      import controllers._
      import controllers.routes._
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(ChargeTypePage)(chargeA.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, versionInt), optUA(ChargeTypeShortService)),
        row(ChargeTypePage)(chargeB.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, versionInt), optUA(ChargeTypeLumpSumDeath)),
        row(ChargeTypePage)(chargeC.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, versionInt), optUA(ChargeTypeAuthSurplus)),
        row(ChargeTypePage)(chargeE.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, versionInt), optUA(ChargeTypeAnnualAllowance)),
        row(ChargeTypePage)(chargeE.routes.MemberDetailsController.onPageLoad(NormalMode,srn, startDate, accessType, versionInt, 1), chargeEMemberExists),
        row(ChargeTypePage)(chargeF.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, versionInt), optUA(ChargeTypeDeRegistration)),
        row(ChargeTypePage)(chargeD.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, versionInt), optUA(ChargeTypeLifetimeAllowance)),
        row(ChargeTypePage)(chargeD.routes.MemberDetailsController.onPageLoad(NormalMode,srn, startDate, accessType, versionInt, 1), chargeDMemberExists),
        row(ChargeTypePage)(chargeG.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, versionInt), optUA(ChargeTypeOverseasTransfer)),
        row(ChargeTypePage)(chargeG.routes.MemberDetailsController.onPageLoad(NormalMode,srn, startDate, accessType, versionInt, 1), chargeGMemberExists),
        row(ChargeTypePage)(routes.SessionExpiredController.onPageLoad()),
        row(ConfirmSubmitAFTReturnPage)(DeclarationController.onPageLoad(srn, startDate, accessType, versionInt), confirmSubmitAFTReturn(confirmSubmit = true)),
        row(ConfirmSubmitAFTReturnPage)(Call("GET", config.managePensionsSchemeSummaryUrl.format(srn)), confirmSubmitAFTReturn(confirmSubmit = false)),
        row(ConfirmSubmitAFTAmendmentPage)(controllers.routes.DeclarationController.onPageLoad(srn, startDate, accessType, versionInt)),
        row(DeclarationPage)(controllers.routes.ConfirmationController.onPageLoad(srn, startDate, accessType, versionInt))
      )
    }

    def normalModeRoutesPsp: TableFor3[Page, UserAnswers, Call] = {
      import controllers.routes._
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(ConfirmSubmitAFTReturnPage)(EnterPsaIdController.onPageLoad(srn, startDate, accessType, versionInt), confirmSubmitAFTReturn(confirmSubmit = true)),
        row(ConfirmSubmitAFTAmendmentPage)(EnterPsaIdController.onPageLoad(srn, startDate, accessType, versionInt), confirmAmendAFTReturn(confirmAmend = true)),
        row(EnterPsaIdPage)(DeclarationController.onPageLoad(srn, startDate, accessType, versionInt),Some(SampleData.userAnswersWithSchemeNamePstrQuarter))


      )
    }

    behave like navigatorWithRoutesForMode(NormalMode)(navigator,
      normalModeRoutes,
      srn,
      startDate,
      accessType,
      versionInt,
      request(pspId=None, psaId = Some(SampleData.psaId))
    )

    behave like navigatorWithRoutesForMode(NormalMode)(navigator,
      normalModeRoutesPsp,
      srn,
      startDate,
      accessType,
      versionInt,
      request(pspId=Some(SampleData.pspId), psaId = None)
    )
  }

  "NormalMode for AFT Summary Page" must {
    def normalModeRoutes: TableFor5[Page, UserAnswers, Call, LocalDate, Int] =
      Table(
        ("Id", "UserAnswers", "Next Page", "Current Date", "Version"),
        rowWithDateAndVersion(AFTSummaryPage)(controllers.routes.ChargeTypeController.onPageLoad(srn, startDate, accessType, versionInt),
          aftSummaryYes, currentDate = LocalDate.now, version = 1),
        rowWithDateAndVersion(AFTSummaryPage)(controllers.routes.SessionExpiredController.onPageLoad(), currentDate = LocalDate.now, version = 1),

        rowWithDateAndVersion(AFTSummaryPage)(
          controllers.routes.ConfirmSubmitAFTReturnController.onPageLoad(srn, startDate),
          aftSummaryNo(quarter = SampleData.q32020),
          currentDate = SampleData.q32020.endDate.plusDays(1),
          version = 1),

        rowWithDateAndVersion(AFTSummaryPage)(
          controllers.amend.routes.ConfirmSubmitAFTAmendmentController.onPageLoad(srn, startDate, accessType, version = 2),
          aftSummaryNo(quarter = SampleData.q32020),
          currentDate = SampleData.q32020.endDate.plusDays(1),
          version = 2),

        rowWithDateAndVersion(AFTSummaryPage)(
          Call("GET", config.managePensionsSchemeSummaryUrl.format(srn)),
          aftSummaryNo(quarter = SampleData.q32020),
          currentDate = SampleData.q32020.endDate,
          version = 1)
      )

    behave like navigatorWithRoutesForModeDateAndVersion(NormalMode)(navigator, normalModeRoutes, srn, startDate, accessType, versionInt)


  }
}
