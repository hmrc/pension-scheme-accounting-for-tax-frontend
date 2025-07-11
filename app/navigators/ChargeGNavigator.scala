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

import com.google.inject.Inject
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.chargeG.routes._
import helpers.{ChargeServiceHelper, DeleteChargeHelper}
import models.ChargeType.ChargeTypeOverseasTransfer
import models.LocalDateBinder._
import models.fileUpload.InputSelection.{FileUploadInput, ManualInput}
import models.requests.DataRequest
import models.{AccessType, MemberDetails, NormalMode, UploadId, UserAnswers}
import pages.Page
import pages.chargeG._
import pages.fileUpload.{FileUploadPage, InputSelectionPage}
import play.api.mvc.{AnyContent, Call}

import java.time.LocalDate
class ChargeGNavigator @Inject()(val dataCacheConnector: UserAnswersCacheConnector,
                                 deleteChargeHelper: DeleteChargeHelper,
                                 chargeServiceHelper: ChargeServiceHelper,
                                 config: FrontendAppConfig)
  extends Navigator {

  def nextIndex(ua: UserAnswers) : Int =
    ua.getAllMembersInCharge[MemberDetails](charge = "chargeGDetails").size

  def addMembers(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
  : Call = ua.get(AddMembersPage) match {
    case Some(true) => MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, version,
      nextIndex(ua))
    case _          => controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version)
  }

  def deleteMemberRoutes(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
                        (implicit request: DataRequest[AnyContent]): Call =
    if(deleteChargeHelper.allChargesDeletedOrZeroed(ua) && !request.isAmendment) {
      Call("GET", config.managePensionsSchemeSummaryUrl.replace("%s", srn))
    } else if (chargeServiceHelper.isEmployerOrMemberPresent(ua, "chargeGDetails")) {
      AddMembersController.onPageLoad(srn, startDate, accessType, version)
    } else {
      controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version)
    }

  //scalastyle:off cyclomatic.complexity
  override protected def routeMap(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
                                 (implicit request: DataRequest[AnyContent]): PartialFunction[Page, Call] = {
    case WhatYouWillNeedPage      => MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, version,
      nextIndex(ua))
    case InputSelectionPage(ChargeTypeOverseasTransfer) => inputSelectionNav(ua, srn, startDate, accessType, version)

    // TODO: Refactor magic strings
    case pages.fileUpload.WhatYouWillNeedPage(ChargeTypeOverseasTransfer) =>
      controllers.fileUpload.routes.FileUploadController.onPageLoad(srn, startDate, accessType, version, ChargeTypeOverseasTransfer)
    case FileUploadPage(ChargeTypeOverseasTransfer) =>
      controllers.fileUpload.routes.ValidationController.onPageLoad(srn, startDate, accessType, version, ChargeTypeOverseasTransfer, UploadId(""))

    case MemberDetailsPage(index) => ChargeDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, version, index)
    case ChargeDetailsPage(index) => ChargeAmountsController.onPageLoad(NormalMode, srn, startDate, accessType, version, index)
    case ChargeAmountsPage(index) => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
    case CheckYourAnswersPage     => AddMembersController.onPageLoad(srn, startDate, accessType, version)
    case AddMembersPage           => addMembers(ua, srn, startDate, accessType, version)
    case DeleteMemberPage         => deleteMemberRoutes(ua, srn, startDate, accessType, version)
  }

  private def inputSelectionNav(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Call = {
    ua.get(InputSelectionPage(ChargeTypeOverseasTransfer)) match {
      case Some(ManualInput) =>
        controllers.chargeG.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, version)
      case Some(FileUploadInput) =>
        controllers.fileUpload.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, version, ChargeTypeOverseasTransfer)
      case _ => sessionExpiredPage
    }
  }

  override protected def editRouteMap(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
                                     (implicit request: DataRequest[AnyContent]): PartialFunction[Page, Call] = {
    case MemberDetailsPage(index) => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
    case ChargeDetailsPage(index) => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
    case ChargeAmountsPage(index) => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
  }

  private val sessionExpiredPage = controllers.routes.SessionExpiredController.onPageLoad
}