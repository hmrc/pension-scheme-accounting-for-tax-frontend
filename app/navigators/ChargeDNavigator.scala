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

import com.google.inject.Inject
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.chargeD.routes.MemberDetailsController
import controllers.chargeD.routes._
import models.NormalMode
import models.UserAnswers
import pages.Page
import pages.chargeD.AddMembersPage
import pages.chargeD._
import play.api.mvc.Call
import services.ChargeDService._
import java.time.LocalDate

import models.LocalDateBinder._
import services.AFTReturnTidyService

class ChargeDNavigator @Inject()(val dataCacheConnector: UserAnswersCacheConnector,
                                 aftReturnTidyService: AFTReturnTidyService,
                                 config: FrontendAppConfig)
    extends Navigator {

  def nextIndex(ua: UserAnswers, srn: String, startDate: LocalDate): Int =
    getLifetimeAllowanceMembersIncludingDeleted(ua, srn, startDate).size

  def addMembers(ua: UserAnswers, srn: String, startDate: LocalDate): Call = ua.get(AddMembersPage) match {
    case Some(true) => MemberDetailsController.onPageLoad(NormalMode, srn, startDate, nextIndex(ua, srn, startDate))
    case _          => controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, None)
  }

  override protected def routeMap(ua: UserAnswers, srn: String, startDate: LocalDate): PartialFunction[Page, Call] = {
    case WhatYouWillNeedPage                                                          => MemberDetailsController.onPageLoad(NormalMode, srn, startDate, nextIndex(ua, srn, startDate))
    case MemberDetailsPage(index)                                                     => ChargeDetailsController.onPageLoad(NormalMode, srn, startDate, index)
    case ChargeDetailsPage(index)                                                     => CheckYourAnswersController.onPageLoad(srn, startDate, index)
    case CheckYourAnswersPage                                                         => AddMembersController.onPageLoad(srn, startDate)
    case AddMembersPage                                                               => addMembers(ua, srn, startDate)
    case DeleteMemberPage if getLifetimeAllowanceMembers(ua, srn, startDate).nonEmpty => AddMembersController.onPageLoad(srn, startDate)
    case DeleteMemberPage if aftReturnTidyService.isAtLeastOneValidCharge(ua) =>
      controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, None)
    case DeleteMemberPage => Call("GET", config.managePensionsSchemeSummaryUrl.format(srn))
  }

  override protected def editRouteMap(ua: UserAnswers, srn: String, startDate: LocalDate): PartialFunction[Page, Call] = {
    case MemberDetailsPage(index) => CheckYourAnswersController.onPageLoad(srn, startDate, index)
    case ChargeDetailsPage(index) => CheckYourAnswersController.onPageLoad(srn, startDate, index)
  }
}
