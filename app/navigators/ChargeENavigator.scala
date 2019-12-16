/*
 * Copyright 2019 HM Revenue & Customs
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
import connectors.cache.UserAnswersCacheConnector
import models.{NormalMode, UserAnswers}
import pages.Page
import pages.chargeE.{AddMembersPage, AnnualAllowanceYearPage, ChargeDetailsPage, CheckYourAnswersPage, DeleteMemberPage, MemberDetailsPage, WhatYouWillNeedPage}
import play.api.mvc.Call
import controllers.chargeE.routes._

class ChargeENavigator @Inject()(val dataCacheConnector: UserAnswersCacheConnector) extends Navigator {

  def nextIndex(ua: UserAnswers, srn: String): Int = ua.getAnnualAllowanceMembersIncludingDeleted(srn).size

  def addMembers(ua: UserAnswers, srn: String): Call = ua.get(AddMembersPage) match {
    case Some(true) => MemberDetailsController.onPageLoad(NormalMode, srn, nextIndex(ua, srn))
    case _ => controllers.routes.IndexController.onPageLoad()
  }

  override protected def routeMap(ua: UserAnswers, srn: String): PartialFunction[Page, Call] = {
    case WhatYouWillNeedPage => MemberDetailsController.onPageLoad(NormalMode, srn, nextIndex(ua, srn))
    case MemberDetailsPage(index) => AnnualAllowanceYearController.onPageLoad(NormalMode, srn, index)
    case AnnualAllowanceYearPage(index) => ChargeDetailsController.onPageLoad(NormalMode, srn, index)
    case ChargeDetailsPage(index) => CheckYourAnswersController.onPageLoad(srn, index)
    case CheckYourAnswersPage => AddMembersController.onPageLoad(srn)
    case AddMembersPage => addMembers(ua, srn)
    case DeleteMemberPage if ua.getAnnualAllowanceMembers(srn).nonEmpty => AddMembersController.onPageLoad(srn)
    case DeleteMemberPage => AddMembersController.onPageLoad(srn) //TODO change to AFT summary page once it is merged in
  }

  override protected def editRouteMap(ua: UserAnswers, srn: String): PartialFunction[Page, Call] = {
    case MemberDetailsPage(index) => CheckYourAnswersController.onPageLoad(srn, index)
    case AnnualAllowanceYearPage(index) => CheckYourAnswersController.onPageLoad(srn, index)
    case ChargeDetailsPage(index) => CheckYourAnswersController.onPageLoad(srn, index)
  }
}
