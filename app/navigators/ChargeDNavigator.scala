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

import com.google.inject.Inject
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.chargeD.routes._
import helpers.DeleteChargeHelper
import models.LocalDateBinder._
import models.requests.DataRequest
import models.{AccessType, MemberDetails, NormalMode, UploadId, UserAnswers}
import pages.Page
import pages.chargeD._
import pages.fileUpload.{FileUploadPage, InputSelectionManualPage, InputSelectionUploadPage}
import play.api.mvc.{AnyContent, Call}
import services.ChargeDService

import java.time.LocalDate

class ChargeDNavigator @Inject()(val dataCacheConnector: UserAnswersCacheConnector,
                                 deleteChargeHelper: DeleteChargeHelper,
                                 chargeDHelper: ChargeDService,
                                 config: FrontendAppConfig)
  extends Navigator {

  def nextIndex(ua: UserAnswers)(implicit request: DataRequest[AnyContent]): Int =
    ua.getAllMembersInCharge[MemberDetails](charge = "chargeDDetails").size

  def addMembers(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
                (implicit request: DataRequest[AnyContent]): Call = ua.get(AddMembersPage) match {
    case Some(true) => MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, version,
      nextIndex(ua))
    case _          => controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version)
  }

  def deleteMemberRoutes(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
                        (implicit request: DataRequest[AnyContent]): Call =
    if(deleteChargeHelper.allChargesDeletedOrZeroed(ua) && !request.isAmendment) {
      Call("GET", config.managePensionsSchemeSummaryUrl.format(srn))
    } else if(chargeDHelper.getLifetimeAllowanceMembers(ua, srn, startDate, accessType, version).nonEmpty) {
      AddMembersController.onPageLoad(srn, startDate, accessType, version)
    } else {
      controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version)
    }

  override protected def routeMap(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
                                 (implicit request: DataRequest[AnyContent]): PartialFunction[Page, Call] = {
    case WhatYouWillNeedPage => MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, version,
      nextIndex(ua))

    case InputSelectionManualPage("lifetime-allowance-charge") => controllers.chargeD.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, version)
    case InputSelectionUploadPage("lifetime-allowance-charge")  => controllers.fileUpload.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, version, "lifetime-allowance-charge")
    case pages.fileUpload.WhatYouWillNeedPage("lifetime-allowance-charge") => controllers.fileUpload.routes.FileUploadController.onPageLoad(srn, startDate, accessType, version, "lifetime-allowance-charge")
    case FileUploadPage("lifetime-allowance-charge") => controllers.fileUpload.routes.ValidationController.onPageLoad(srn, startDate, accessType, version, "lifetime-allowance-charge", UploadId(""))

    case MemberDetailsPage(index) => ChargeDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, version, index)
    case ChargeDetailsPage(index) => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
    case CheckYourAnswersPage => AddMembersController.onPageLoad(srn, startDate, accessType, version)
    case AddMembersPage => addMembers(ua, srn, startDate, accessType, version)
    case DeleteMemberPage  => deleteMemberRoutes(ua, srn, startDate, accessType, version)
  }

  override protected def editRouteMap(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
                                     (implicit request: DataRequest[AnyContent]): PartialFunction[Page, Call] = {
    case MemberDetailsPage(index) => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
    case ChargeDetailsPage(index) => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
  }
}
