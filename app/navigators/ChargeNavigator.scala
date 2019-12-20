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
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import models.{ChargeType, NormalMode, UserAnswers}
import pages.{AFTSummaryPage, ChargeTypePage, Page}
import play.api.mvc.Call

class ChargeNavigator @Inject()(config: FrontendAppConfig, val dataCacheConnector: UserAnswersCacheConnector) extends Navigator {

  def nextIndexChargeE(ua: UserAnswers, srn: String): Int = ua.getAnnualAllowanceMembersIncludingDeleted(srn).size

  override protected def routeMap(ua: UserAnswers, srn: String): PartialFunction[Page, Call] = {
    case ChargeTypePage => chargeTypeNavigation(ua, srn)
    case AFTSummaryPage => aftSummaryNavigation(ua, srn)
  }

  override protected def editRouteMap(ua: UserAnswers, srn: String): PartialFunction[Page, Call] = {
    case ChargeTypePage => sessionExpiredPage
  }

  private def chargeTypeNavigation(ua:UserAnswers, srn:String):Call =
    ua.get(ChargeTypePage) match {
      case Some(ChargeType.ChargeTypeShortService) => controllers.chargeA.routes.WhatYouWillNeedController.onPageLoad(srn)
      case Some(ChargeType.ChargeTypeLumpSumDeath) => controllers.chargeB.routes.WhatYouWillNeedController.onPageLoad(srn)
      case Some(ChargeType.ChargeTypeAnnualAllowance) if nextIndexChargeE(ua, srn) == 0 => controllers.chargeE.routes.WhatYouWillNeedController.onPageLoad(srn)
      case Some(ChargeType.ChargeTypeAnnualAllowance) => controllers.chargeE.routes.MemberDetailsController.onPageLoad(NormalMode, srn, nextIndexChargeE(ua, srn))
      case Some(ChargeType.ChargeTypeDeRegistration) => controllers.chargeF.routes.WhatYouWillNeedController.onPageLoad(srn)
      case _ => sessionExpiredPage
    }


  private def aftSummaryNavigation(ua: UserAnswers, srn: String): Call =
    ua.get(AFTSummaryPage) match {
      case Some(true) => controllers.routes.ChargeTypeController.onPageLoad(NormalMode, srn)
      case _ => Call("GET", config.managePensionsSchemeSummaryUrl.format(srn))
    }

  private val sessionExpiredPage = controllers.routes.SessionExpiredController.onPageLoad()
}
