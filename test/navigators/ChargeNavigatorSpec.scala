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

import controllers.fileUpload.routes.InputSelectionController
import controllers.routes._
import controllers.{chargeD, chargeE, chargeG}
import data.SampleData
import data.SampleData._
import models.ChargeType._
import models.LocalDateBinder._
import models.{AFTQuarter, AccessMode, ChargeType, NormalMode, UserAnswers}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.prop.{TableFor3, TableFor5}
import org.scalatestplus.mockito.MockitoSugar
import pages._
import play.api.Application
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.mvc.Call
import utils.AFTConstants._

import java.time.LocalDate

class ChargeNavigatorSpec extends NavigatorBehaviour with MockitoSugar with BeforeAndAfterEach {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .overrides(
      Seq[GuiceableModule](
      ): _*
    ).build()

  private val navigator: CompoundNavigator = injector.instanceOf[CompoundNavigator]

  override def beforeEach(): Unit = {
    super.beforeEach()
  }

  private val srn = "test-srn"
  private val startDate = QUARTER_START_DATE

  private def optUA(ct: ChargeType): Option[UserAnswers] = SampleData.userAnswersWithSchemeNamePstrQuarter.set(ChargeTypePage, ct).toOption

  private def aftSummaryYes: Option[UserAnswers] = UserAnswers().set(AFTSummaryPage, true).toOption

  private def aftSummaryNo(quarter: AFTQuarter): Option[UserAnswers] =
    Option(
      UserAnswers()
        .setOrException(AFTSummaryPage, false)
        .setOrException(QuarterPage, quarter)
    )

  private def confirmSubmitAFTReturn(confirmSubmit: Boolean): Option[UserAnswers] =
    Option(UserAnswers().setOrException(ConfirmSubmitAFTReturnPage, confirmSubmit))

  private def confirmAmendAFTReturn(confirmAmend: Boolean): Option[UserAnswers] =
    Option(UserAnswers().setOrException(ConfirmSubmitAFTAmendmentPage, confirmAmend))

  "NormalMode" must {
    def normalModeRoutes: TableFor3[Page, UserAnswers, Call] = {
      import controllers._
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(ChargeTypePage)(chargeA.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, versionInt), optUA(ChargeTypeShortService)),
        row(ChargeTypePage)(chargeB.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, versionInt), optUA(ChargeTypeLumpSumDeath)),
        row(ChargeTypePage)(chargeC.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, versionInt), optUA(ChargeTypeAuthSurplus)),
        row(ChargeTypePage)(chargeF.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, versionInt), optUA(ChargeTypeDeRegistration)),
        row(ChargeTypePage)(routes.SessionExpiredController.onPageLoad),
        row(ConfirmSubmitAFTReturnPage)(DeclarationController.onPageLoad(srn, startDate, accessType, versionInt), confirmSubmitAFTReturn(confirmSubmit = true)),
        row(ConfirmSubmitAFTReturnPage)(controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt),
          confirmSubmitAFTReturn(confirmSubmit = false)),
        row(ConfirmSubmitAFTAmendmentPage)(controllers.routes.DeclarationController.onPageLoad(srn, startDate, accessType, versionInt)),
        row(DeclarationPage)(controllers.routes.ConfirmationController.onPageLoad(srn, startDate, accessType, versionInt))
      )
    }

    def normalModeRoutesPsp: TableFor3[Page, UserAnswers, Call] = {
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(ConfirmSubmitAFTReturnPage)(EnterPsaIdController.onPageLoad(srn, startDate, accessType, versionInt), confirmSubmitAFTReturn(confirmSubmit = true)),
        row(ConfirmSubmitAFTReturnPage)(controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt),
          confirmSubmitAFTReturn(confirmSubmit = false)),
        row(ConfirmSubmitAFTAmendmentPage)(EnterPsaIdController.onPageLoad(srn, startDate, accessType, versionInt), confirmAmendAFTReturn(confirmAmend = true)),
        row(EnterPsaIdPage)(DeclarationController.onPageLoad(srn, startDate, accessType, versionInt), Some(SampleData.userAnswersWithSchemeNamePstrQuarter))
      )
    }

    behave like navigatorWithRoutesForMode(NormalMode)(navigator,
      normalModeRoutes,
      srn,
      startDate,
      accessType,
      versionInt,
      request(pspId = None, psaId = Some(SampleData.psaId))
    )

    behave like navigatorWithRoutesForMode(NormalMode)(navigator,
      normalModeRoutesPsp,
      srn,
      startDate,
      accessType,
      versionInt,
      request(pspId = Some(SampleData.pspId), psaId = None)
    )
  }

  "NormalMode for AFT Summary Page" must {
    def normalModeRoutes: TableFor5[Page, UserAnswers, Call, LocalDate, Int] =
      Table(
        ("Id", "UserAnswers", "Next Page", "Current Date", "Version"),
        rowWithDateAndVersion(AFTSummaryPage)(controllers.routes.ChargeTypeController.onPageLoad(srn, startDate, accessType, versionInt),
          aftSummaryYes, currentDate = LocalDate.now, version = 1),
        rowWithDateAndVersion(AFTSummaryPage)(controllers.routes.SessionExpiredController.onPageLoad, currentDate = LocalDate.now, version = 1),

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
          controllers.routes.NotSubmissionTimeController.onPageLoad(srn, startDate),
          aftSummaryNo(quarter = SampleData.q32020),
          currentDate = SampleData.q32020.endDate,
          version = 1)
      )

    behave like navigatorWithRoutesForModeDateAndVersion(NormalMode)(navigator, normalModeRoutes, srn, startDate, accessType, versionInt)
  }
}


class ChargeNavigatorToggleOnSpec extends NavigatorBehaviour with MockitoSugar with BeforeAndAfterEach {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .overrides(Seq[GuiceableModule](): _*).build()

  private val navigator: CompoundNavigator = injector.instanceOf[CompoundNavigator]

  override def beforeEach(): Unit = {
    super.beforeEach()
  }

  private val srn = "test-srn"
  private val startDate = QUARTER_START_DATE


  private def chargeEMemberExists(chargeType: ChargeType = ChargeTypeAnnualAllowance, version: Int = 1, memberStatus: String = "New"): Option[UserAnswers] =
    SampleData.chargeEMember.set(ChargeTypePage, chargeType).toOption.get
      .set(pages.chargeE.MemberStatusPage(0), memberStatus).toOption.get
      .set(pages.chargeE.MemberAFTVersionPage(0), version).toOption

  private def chargeDMemberExists(chargeType: ChargeType = ChargeTypeLifetimeAllowance, version: Int = 1, memberStatus: String = "New"): Option[UserAnswers] =
    SampleData.chargeDMember.set(ChargeTypePage, chargeType).toOption.get
      .set(pages.chargeD.MemberStatusPage(0), memberStatus).toOption.get
      .set(pages.chargeD.MemberAFTVersionPage(0), version).toOption

  private def chargeGMemberExists(chargeType: ChargeType = ChargeTypeOverseasTransfer, version: Int = 1, memberStatus: String = "New"): Option[UserAnswers] =
    SampleData.chargeGMember.set(ChargeTypePage, chargeType).toOption.get
      .set(pages.chargeG.MemberStatusPage(0), memberStatus).toOption.get
      .set(pages.chargeG.MemberAFTVersionPage(0), version).toOption


  "NormalMode when bulk upload toggle is switched on" must {
    def normalModeRoutes: TableFor5[Page, UserAnswers, Call, AccessMode, Int] = {
      Table(
        ("Id", "UserAnswers", "Next Page", "AccessMode", "version"),
        rowWithAccessModeAndVersion(ChargeTypePage)(InputSelectionController.onPageLoad(srn, startDate, accessType, versionInt, ChargeTypeAnnualAllowance),
          chargeEMemberExists(), AccessMode.PageAccessModeCompile, 1),
        rowWithAccessModeAndVersion(ChargeTypePage)(InputSelectionController.onPageLoad(srn, startDate, accessType, versionInt, ChargeTypeLifetimeAllowance),
          chargeDMemberExists(), AccessMode.PageAccessModeCompile, 1),
        rowWithAccessModeAndVersion(ChargeTypePage)(InputSelectionController.onPageLoad(srn, startDate, accessType, versionInt, ChargeTypeOverseasTransfer),
          chargeGMemberExists(), AccessMode.PageAccessModeCompile, 1),
        //No member
        rowWithAccessModeAndVersion(ChargeTypePage)(InputSelectionController.onPageLoad(srn, startDate, accessType, version2Int, ChargeTypeAnnualAllowance),
          chargeDMemberExists(ChargeTypeAnnualAllowance), AccessMode.PageAccessModePreCompile, 2),
        rowWithAccessModeAndVersion(ChargeTypePage)(InputSelectionController.onPageLoad(srn, startDate, accessType, version2Int, ChargeTypeLifetimeAllowance),
          chargeEMemberExists(ChargeTypeLifetimeAllowance), AccessMode.PageAccessModePreCompile, 2),
        rowWithAccessModeAndVersion(ChargeTypePage)(InputSelectionController.onPageLoad(srn, startDate, accessType, version2Int, ChargeTypeOverseasTransfer),
          chargeDMemberExists(ChargeTypeOverseasTransfer), AccessMode.PageAccessModePreCompile, 2),
        //With member
        rowWithAccessModeAndVersion(ChargeTypePage)(chargeE.routes.MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, version2Int, 1),
          chargeEMemberExists(), AccessMode.PageAccessModePreCompile, 2),
        rowWithAccessModeAndVersion(ChargeTypePage)(chargeD.routes.MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, version2Int, 1),
          chargeDMemberExists(), AccessMode.PageAccessModePreCompile, 2),
        rowWithAccessModeAndVersion(ChargeTypePage)(chargeG.routes.MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, version2Int, 1),
          chargeGMemberExists(), AccessMode.PageAccessModePreCompile, 2),

        rowWithAccessModeAndVersion(ChargeTypePage)(InputSelectionController.onPageLoad(srn, startDate, accessType, version2Int, ChargeTypeAnnualAllowance),
          chargeEMemberExists(version = 2), AccessMode.PageAccessModeCompile, 2),
        rowWithAccessModeAndVersion(ChargeTypePage)(InputSelectionController.onPageLoad(srn, startDate, accessType, version2Int, ChargeTypeLifetimeAllowance),
          chargeDMemberExists(version = 2), AccessMode.PageAccessModeCompile, 2),
        rowWithAccessModeAndVersion(ChargeTypePage)(InputSelectionController.onPageLoad(srn, startDate, accessType, version2Int, ChargeTypeOverseasTransfer),
          chargeGMemberExists(version = 2), AccessMode.PageAccessModeCompile, 2),

        rowWithAccessModeAndVersion(ChargeTypePage)(chargeE.routes.MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, 3, 1),
          chargeEMemberExists(version = 2, memberStatus = "Changed"), AccessMode.PageAccessModeCompile, 3),
        rowWithAccessModeAndVersion(ChargeTypePage)(chargeD.routes.MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, 3, 1),
          chargeDMemberExists(version = 2, memberStatus = "Changed"), AccessMode.PageAccessModeCompile, 3),
        rowWithAccessModeAndVersion(ChargeTypePage)(chargeG.routes.MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, 3, 1),
          chargeGMemberExists(version = 2, memberStatus = "Changed"), AccessMode.PageAccessModeCompile, 3),

      )
    }

    behave like navigatorWithRoutesForModeAccessModeAndVersion(NormalMode)(navigator,
      normalModeRoutes,
      srn,
      startDate,
      accessType,
      versionInt)
  }
}
