/*
 * Copyright 2022 HM Revenue & Customs
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

import controllers.chargeE
import controllers.chargeD
import controllers.fileUpload.routes.InputSelectionController
import data.SampleData
import data.SampleData.{accessType, chargeAChargeDetails, chargeAmount1, chargeAmount3, chargeDDetails, chargeEDetails, memberDetails, userAnswersWithSchemeNamePstrQuarter, versionInt}
import models.ChargeType._
import models.FeatureToggle.{Disabled, Enabled}
import models.FeatureToggleName.AftBulkUpload
import models.LocalDateBinder._
import models.{AFTQuarter, ChargeType, NormalMode, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.prop.{TableFor3, TableFor5}
import pages._
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.mvc.Call
import services.FeatureToggleService
import utils.AFTConstants._

import java.time.LocalDate
import scala.concurrent.Future

class ChargeNavigatorSpec extends NavigatorBehaviour with MockitoSugar with BeforeAndAfterEach {
  private val mockFeatureToggleService = mock[FeatureToggleService]

  override lazy val app: Application = new GuiceApplicationBuilder()
    .overrides(
      Seq[GuiceableModule](
        bind[FeatureToggleService].toInstance(mockFeatureToggleService)
      ): _*
    ).build()

  private val navigator: CompoundNavigator = injector.instanceOf[CompoundNavigator]

  override def beforeEach: Unit = {
    super.beforeEach
    when(mockFeatureToggleService.get(any())(any(), any())).thenReturn(Future.successful(Disabled(AftBulkUpload)))
  }

  private val srn = "test-srn"
  private val startDate = QUARTER_START_DATE

  private def optUA(ct: ChargeType): Option[UserAnswers] = SampleData.userAnswersWithSchemeNamePstrQuarter.set(ChargeTypePage, ct).toOption

  private def chargeEMemberExists: Option[UserAnswers] = SampleData.chargeEMember.set(ChargeTypePage, ChargeTypeAnnualAllowance).toOption

  private def chargeDMemberExists: Option[UserAnswers] = SampleData.chargeDMember.set(ChargeTypePage, ChargeTypeLifetimeAllowance).toOption

  private def chargeGMemberExists: Option[UserAnswers] = SampleData.chargeGMember.set(ChargeTypePage, ChargeTypeOverseasTransfer).toOption

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
      import controllers.routes._
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(ChargeTypePage)(chargeA.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, versionInt), optUA(ChargeTypeShortService)),
        row(ChargeTypePage)(chargeB.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, versionInt), optUA(ChargeTypeLumpSumDeath)),
        row(ChargeTypePage)(chargeC.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, versionInt), optUA(ChargeTypeAuthSurplus)),
        row(ChargeTypePage)(chargeE.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, versionInt), optUA(ChargeTypeAnnualAllowance)),
        row(ChargeTypePage)(chargeE.routes.MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt, 1), chargeEMemberExists),
        row(ChargeTypePage)(chargeF.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, versionInt), optUA(ChargeTypeDeRegistration)),
        row(ChargeTypePage)(chargeD.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, versionInt), optUA(ChargeTypeLifetimeAllowance)),
        row(ChargeTypePage)(chargeD.routes.MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt, 1), chargeDMemberExists),
        row(ChargeTypePage)(chargeG.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, versionInt), optUA(ChargeTypeOverseasTransfer)),
        row(ChargeTypePage)(chargeG.routes.MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt, 1), chargeGMemberExists),
        row(ChargeTypePage)(routes.SessionExpiredController.onPageLoad),
        row(ConfirmSubmitAFTReturnPage)(DeclarationController.onPageLoad(srn, startDate, accessType, versionInt), confirmSubmitAFTReturn(confirmSubmit = true)),
        row(ConfirmSubmitAFTReturnPage)(controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt),
          confirmSubmitAFTReturn(confirmSubmit = false)),
        row(ConfirmSubmitAFTAmendmentPage)(controllers.routes.DeclarationController.onPageLoad(srn, startDate, accessType, versionInt)),
        row(DeclarationPage)(controllers.routes.ConfirmationController.onPageLoad(srn, startDate, accessType, versionInt))
      )
    }

    def normalModeRoutesPsp: TableFor3[Page, UserAnswers, Call] = {
      import controllers.routes._
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
  private val mockFeatureToggleService = mock[FeatureToggleService]

  override lazy val app: Application = new GuiceApplicationBuilder()
    .overrides(
      Seq[GuiceableModule](
        bind[FeatureToggleService].toInstance(mockFeatureToggleService)
      ): _*
    ).build()

  private val navigator: CompoundNavigator = injector.instanceOf[CompoundNavigator]

  override def beforeEach: Unit = {
    super.beforeEach
    when(mockFeatureToggleService.get(any())(any(), any())).thenReturn(Future.successful(Enabled(AftBulkUpload)))
  }

  private val srn = "test-srn"
  private val startDate = QUARTER_START_DATE


  private def chargeEMemberExists: Option[UserAnswers] = SampleData.chargeEMember.set(ChargeTypePage, ChargeTypeAnnualAllowance).toOption

  /*We have a Annual Allowance Charge members,Lifetime Allowance members & Short service refund Charge.
   * 1.Annual Allowance Charge -> navigate to member detail
   * 2.LifetimeAllowance -> navigate to member detail
   * 3.OverseasTransfer  -> navigate to File Upload
   */
  private def fileUploadMemberScenario1(chargeType: ChargeType): Option[UserAnswers] = userAnswersWithSchemeNamePstrQuarter
    .set(pages.chargeE.MemberDetailsPage(0), memberDetails).toOption.get
    .set(pages.chargeE.ChargeDetailsPage(0), chargeEDetails).toOption.get
    .set(pages.chargeE.TotalChargeAmountPage, chargeAmount1).toOption.get
    .set(pages.chargeE.MemberStatusPage(0),"New").toOption.get
    .set(pages.chargeE.MemberAFTVersionPage(0),1).toOption.get
    .set(pages.chargeD.MemberDetailsPage(0), memberDetails).toOption.get
    .set(pages.chargeD.ChargeDetailsPage(0), chargeDDetails).toOption.get
    .set(pages.chargeD.TotalChargeAmountPage, chargeAmount3).toOption.get
    .set(pages.chargeD.MemberStatusPage(0),"Changed").toOption.get
    .set(pages.chargeD.MemberAFTVersionPage(0),1).toOption.get
    .set(pages.chargeA.ChargeDetailsPage,chargeAChargeDetails).toOption.get
    .set(pages.ChargeTypePage,chargeType).toOption.get
    .set(AFTStatusQuery,"Submitted").toOption

  private def fileUploadMemberScenario2(chargeType: ChargeType): Option[UserAnswers] = userAnswersWithSchemeNamePstrQuarter
    .set(pages.chargeE.MemberDetailsPage(0), memberDetails).toOption.get
    .set(pages.chargeE.ChargeDetailsPage(0), chargeEDetails).toOption.get
    .set(pages.chargeE.TotalChargeAmountPage, chargeAmount1).toOption.get
    .set(pages.chargeE.MemberStatusPage(0),"Deleted").toOption.get
    .set(pages.chargeE.MemberAFTVersionPage(0),1).toOption.get
    .set(pages.chargeD.MemberDetailsPage(0), memberDetails).toOption.get
    .set(pages.chargeD.ChargeDetailsPage(0), chargeDDetails).toOption.get
    .set(pages.chargeD.TotalChargeAmountPage, chargeAmount3).toOption.get
    .set(pages.chargeD.MemberStatusPage(0),"Deleted").toOption.get
    .set(pages.chargeD.MemberAFTVersionPage(0),1).toOption.get
    .set(pages.chargeA.ChargeDetailsPage,chargeAChargeDetails).toOption.get
    .set(pages.ChargeTypePage,chargeType).toOption.get
    .set(AFTStatusQuery,"Submitted").toOption

  private def fileUploadMemberScenario3(chargeType: ChargeType): Option[UserAnswers] = userAnswersWithSchemeNamePstrQuarter
    .set(pages.chargeE.MemberDetailsPage(0), memberDetails).toOption.get
    .set(pages.chargeE.ChargeDetailsPage(0), chargeEDetails).toOption.get
    .set(pages.chargeE.TotalChargeAmountPage, chargeAmount1).toOption.get
    .set(pages.chargeD.MemberDetailsPage(0), memberDetails).toOption.get
    .set(pages.chargeD.ChargeDetailsPage(0), chargeDDetails).toOption.get
    .set(pages.chargeD.TotalChargeAmountPage, chargeAmount3).toOption.get
    .set(pages.chargeA.ChargeDetailsPage,chargeAChargeDetails).toOption.get
    .set(pages.ChargeTypePage,chargeType).toOption.get
    .set(AFTStatusQuery,"Compiled").toOption

  private def fileUploadMemberScenario4(chargeType: ChargeType): Option[UserAnswers] = userAnswersWithSchemeNamePstrQuarter
    .set(pages.ChargeTypePage,chargeType).get
    .set(AFTStatusQuery,"Submitted").toOption

  private def chargeDMemberExists: Option[UserAnswers] = SampleData.chargeDMember.set(ChargeTypePage, ChargeTypeLifetimeAllowance).toOption

  private def chargeGMemberExists: Option[UserAnswers] = SampleData.chargeGMember.set(ChargeTypePage, ChargeTypeOverseasTransfer).toOption

  private def optUA(ct: ChargeType): Option[UserAnswers] = SampleData.userAnswersWithSchemeNamePstrQuarter.set(ChargeTypePage, ct).toOption

  "NormalMode when bulk upload toggle is switched on" must {
    def normalModeRoutes: TableFor3[Page, UserAnswers, Call] = {
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(ChargeTypePage)(InputSelectionController.onPageLoad(srn, startDate, accessType, versionInt, ChargeTypeAnnualAllowance), chargeEMemberExists),
        row(ChargeTypePage)(InputSelectionController.onPageLoad(srn, startDate, accessType, versionInt, ChargeTypeLifetimeAllowance), chargeDMemberExists),
        row(ChargeTypePage)(InputSelectionController.onPageLoad(srn, startDate, accessType, versionInt, ChargeTypeOverseasTransfer), chargeGMemberExists),
        row(ChargeTypePage)(chargeE.routes.MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt, 1), fileUploadMemberScenario1(ChargeTypeAnnualAllowance)),
        row(ChargeTypePage)(chargeD.routes.MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt, 1), fileUploadMemberScenario1(ChargeTypeLifetimeAllowance)),
        row(ChargeTypePage)(InputSelectionController.onPageLoad(srn, startDate, accessType, versionInt, ChargeTypeOverseasTransfer), fileUploadMemberScenario1(ChargeTypeOverseasTransfer)),
        row(ChargeTypePage)(chargeE.routes.MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt, 1), fileUploadMemberScenario2(ChargeTypeAnnualAllowance)),
        row(ChargeTypePage)(chargeD.routes.MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt, 1), fileUploadMemberScenario2(ChargeTypeLifetimeAllowance)),
        row(ChargeTypePage)(InputSelectionController.onPageLoad(srn, startDate, accessType, versionInt, ChargeTypeAnnualAllowance), fileUploadMemberScenario3(ChargeTypeAnnualAllowance)),
        row(ChargeTypePage)(InputSelectionController.onPageLoad(srn, startDate, accessType, versionInt, ChargeTypeLifetimeAllowance), fileUploadMemberScenario3(ChargeTypeLifetimeAllowance)),
        row(ChargeTypePage)(InputSelectionController.onPageLoad(srn, startDate, accessType, versionInt, ChargeTypeOverseasTransfer), fileUploadMemberScenario3(ChargeTypeOverseasTransfer)),
        row(ChargeTypePage)(InputSelectionController.onPageLoad(srn, startDate, accessType, versionInt, ChargeTypeAnnualAllowance), fileUploadMemberScenario4(ChargeTypeAnnualAllowance)),
        row(ChargeTypePage)(InputSelectionController.onPageLoad(srn, startDate, accessType, versionInt, ChargeTypeLifetimeAllowance), fileUploadMemberScenario4(ChargeTypeLifetimeAllowance)),
        row(ChargeTypePage)(InputSelectionController.onPageLoad(srn, startDate, accessType, versionInt, ChargeTypeOverseasTransfer), fileUploadMemberScenario4(ChargeTypeOverseasTransfer))

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
  }
}
