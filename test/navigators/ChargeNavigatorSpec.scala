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
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.mvc.Call
import utils.AFTConstants._

import java.time.LocalDate

class ChargeNavigatorSpec extends NavigatorBehaviour with MockitoSugar with BeforeAndAfterEach {

  override lazy val app: Application = new GuiceApplicationBuilder().build()

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

  "NormalMode" must {
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
