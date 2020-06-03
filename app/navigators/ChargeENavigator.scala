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

import java.time.LocalDate

import com.google.inject.Inject
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.chargeE.routes._
import helpers.DeleteChargeHelper
import models.LocalDateBinder._
import models.requests.DataRequest
import models.{Draft, NormalMode, UserAnswers}
import pages.Page
import pages.chargeE._
import play.api.mvc.{AnyContent, Call}
import services.ChargeEService
class ChargeENavigator @Inject()(val dataCacheConnector: UserAnswersCacheConnector,
                                 deleteChargeHelper: DeleteChargeHelper,
                                 chargeEHelper: ChargeEService,
                                 config: FrontendAppConfig)
  extends Navigator {

  def nextIndex(ua: UserAnswers, srn: String, startDate: LocalDate)(implicit request: DataRequest[AnyContent]): Int =
    chargeEHelper.getAnnualAllowanceMembers(ua, srn, startDate).size

  def addMembers(ua: UserAnswers, srn: String, startDate: LocalDate)
                (implicit request: DataRequest[AnyContent]): Call = ua.get(AddMembersPage) match {
    case Some(true) => MemberDetailsController.onPageLoad(NormalMode, srn, startDate, Draft, 1, nextIndex(ua, srn, startDate))
    case _          => controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, Draft, 1)
  }

  def deleteMemberRoutes(ua: UserAnswers, srn: String, startDate: LocalDate)
                        (implicit request: DataRequest[AnyContent]): Call =
    if(deleteChargeHelper.allChargesDeletedOrZeroed(ua) && !request.isAmendment) {
      Call("GET", config.managePensionsSchemeSummaryUrl.format(srn))
    } else if(chargeEHelper.getAnnualAllowanceMembers(ua, srn, startDate).nonEmpty) {
        AddMembersController.onPageLoad(srn, startDate, Draft, 1)
     } else {
      controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, Draft, 1)
    }

  override protected def routeMap(ua: UserAnswers, srn: String, startDate: LocalDate)
                                 (implicit request: DataRequest[AnyContent]): PartialFunction[Page, Call] = {
    case WhatYouWillNeedPage => MemberDetailsController.onPageLoad(NormalMode, srn, startDate, Draft, 1, nextIndex(ua, srn, startDate))
    case MemberDetailsPage(index) => AnnualAllowanceYearController.onPageLoad(NormalMode, srn, startDate, Draft, 1, index)
    case AnnualAllowanceYearPage(index) => ChargeDetailsController.onPageLoad(NormalMode, srn, startDate, Draft, 1, index)
    case ChargeDetailsPage(index) => CheckYourAnswersController.onPageLoad(srn, startDate, Draft, 1, index)
    case CheckYourAnswersPage => AddMembersController.onPageLoad(srn, startDate, Draft, 1)
    case AddMembersPage => addMembers(ua, srn, startDate)
    case DeleteMemberPage => deleteMemberRoutes(ua, srn, startDate)
  }

  override protected def editRouteMap(ua: UserAnswers, srn: String, startDate: LocalDate)
                                     (implicit request: DataRequest[AnyContent]): PartialFunction[Page, Call] = {
    case MemberDetailsPage(index)       => CheckYourAnswersController.onPageLoad(srn, startDate, Draft, 1, index)
    case AnnualAllowanceYearPage(index) => CheckYourAnswersController.onPageLoad(srn, startDate, Draft, 1, index)
    case ChargeDetailsPage(index)       => CheckYourAnswersController.onPageLoad(srn, startDate, Draft, 1, index)
  }
}
