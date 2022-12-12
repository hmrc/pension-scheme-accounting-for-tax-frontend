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

import com.google.inject.Inject
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.chargeE.routes._
import helpers.{ChargeServiceHelper, DeleteChargeHelper}
import models.ChargeType.ChargeTypeAnnualAllowance
import models.LocalDateBinder._
import models.fileUpload.InputSelection.{FileUploadInput, ManualInput}
import models.requests.DataRequest
import models.{AccessType, Index, MemberDetails, NormalMode, UploadId, UserAnswers}
import pages.Page
import pages.chargeE._
import pages.fileUpload.{FileUploadPage, InputSelectionPage}
import pages.mccloud._
import play.api.mvc.{AnyContent, Call}

import java.time.LocalDate

class ChargeENavigator @Inject()(val dataCacheConnector: UserAnswersCacheConnector,
                                 deleteChargeHelper: DeleteChargeHelper,
                                 chargeServiceHelper: ChargeServiceHelper,
                                 config: FrontendAppConfig)
  extends Navigator {

  def nextIndex(ua: UserAnswers): Int =
    ua.getAllMembersInCharge[MemberDetails](charge = "chargeEDetails").size

  def addMembers(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
  : Call = ua.get(AddMembersPage) match {
    case Some(true) => MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, version,
      nextIndex(ua))
    case _ => controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version)
  }

  def deleteMemberRoutes(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
                        (implicit request: DataRequest[AnyContent]): Call =
    if (deleteChargeHelper.allChargesDeletedOrZeroed(ua) && !request.isAmendment) {
      Call("GET", config.managePensionsSchemeSummaryUrl.format(srn))
    } else if (chargeServiceHelper.isEmployerOrMemberPresent(ua, "chargeEDetails")) {
      AddMembersController.onPageLoad(srn, startDate, accessType, version)
    } else {
      controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version)
    }

  //scalastyle:off method.length
  //scalastyle:off cyclomatic.complexity
  override protected def routeMap(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
                                 (implicit request: DataRequest[AnyContent]): PartialFunction[Page, Call] = {
    case WhatYouWillNeedPage => MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, version,
      nextIndex(ua))

    case InputSelectionPage(ChargeTypeAnnualAllowance) => inputSelectionNav(ua, srn, startDate, accessType, version)

    // TODO: Refactor magic strings
    case pages.fileUpload.WhatYouWillNeedPage(ChargeTypeAnnualAllowance) =>
      controllers.fileUpload.routes.FileUploadController.onPageLoad(srn, startDate, accessType, version, ChargeTypeAnnualAllowance)
    case FileUploadPage(ChargeTypeAnnualAllowance) =>
      controllers.fileUpload.routes.ValidationController.onPageLoad(srn, startDate, accessType, version, ChargeTypeAnnualAllowance, UploadId(""))

    case MemberDetailsPage(index) => AnnualAllowanceYearController.onPageLoad(NormalMode, srn, startDate, accessType, version, index)
    case AnnualAllowanceYearPage(index) => ChargeDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, version, index)
    case ChargeDetailsPage(index) =>
      controllers.mccloud.routes.IsPublicServicePensionsRemedyController
        .onPageLoad(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, version, index)
    case IsPublicServicePensionsRemedyPage(ChargeTypeAnnualAllowance, index) =>
      ua.get(IsPublicServicePensionsRemedyPage(ChargeTypeAnnualAllowance, index)) match {
        case Some(true) => controllers.mccloud.routes.IsChargeInAdditionReportedController
          .onPageLoad(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, version, index)
        case Some(false) => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
        case _ => sessionExpiredPage
      }

    case IsChargeInAdditionReportedPage(ChargeTypeAnnualAllowance, index) =>
      ua.get(IsChargeInAdditionReportedPage(ChargeTypeAnnualAllowance, index)) match {
        case Some(true) => controllers.mccloud.routes.WasAnotherPensionSchemeController
          .onPageLoad(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, version, index)
        case Some(false) => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
        case _ => sessionExpiredPage
      }

    case WasAnotherPensionSchemePage(ChargeTypeAnnualAllowance, index) =>
      ua.get(WasAnotherPensionSchemePage(ChargeTypeAnnualAllowance, index)) match {
        case Some(true) => controllers.mccloud.routes.EnterPstrController
          .onPageLoad(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, version, index, 0)
        case Some(false) => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
      }

    case EnterPstrPage(ChargeTypeAnnualAllowance, index, schemeIndex) =>
      controllers.mccloud.routes.TaxYearReportedAndPaidController
        .onPageLoadWithIndex(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, version, index, schemeIndex)

    case TaxYearReportedAndPaidPage(ChargeTypeAnnualAllowance, index, schemeIndex) =>
      schemeIndex match {
        case Some(i) => controllers.mccloud.routes.TaxQuarterReportedAndPaidController
          .onPageLoadWithIndex(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, version, index, i)
        case None => controllers.mccloud.routes.TaxQuarterReportedAndPaidController
          .onPageLoad(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, version, index)
      }
    case TaxQuarterReportedAndPaidPage(ChargeTypeAnnualAllowance, index, schemeIndex) =>
      schemeIndex match {
        case Some(i) => controllers.mccloud.routes.ChargeAmountReportedController
          .onPageLoadWithIndex(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, version, index, i)
        case None => controllers.mccloud.routes.ChargeAmountReportedController
          .onPageLoad(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, version, index)
      }
    case ChargeAmountReportedPage(ChargeTypeAnnualAllowance, index, _) =>
      CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)

    case CheckYourAnswersPage => AddMembersController.onPageLoad(srn, startDate, accessType, version)
    case AddMembersPage => addMembers(ua, srn, startDate, accessType, version)
    case DeleteMemberPage => deleteMemberRoutes(ua, srn, startDate, accessType, version)
  }

  private def inputSelectionNav(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Call = {
    ua.get(InputSelectionPage(ChargeTypeAnnualAllowance)) match {
      case Some(ManualInput) =>
        controllers.chargeE.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, version)
      case Some(FileUploadInput) =>
        controllers.fileUpload.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, version, ChargeTypeAnnualAllowance)
      case _ => sessionExpiredPage
    }
  }

  override protected def editRouteMap(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
                                     (implicit request: DataRequest[AnyContent]): PartialFunction[Page, Call] = {
    case MemberDetailsPage(index) => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
    case AnnualAllowanceYearPage(index) => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
    case ChargeDetailsPage(index) => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
  }

  private val sessionExpiredPage = controllers.routes.SessionExpiredController.onPageLoad
}
