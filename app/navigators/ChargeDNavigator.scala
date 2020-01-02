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
import connectors.cache.UserAnswersCacheConnector
import controllers.chargeD.routes._
import models.{NormalMode, UserAnswers}
import pages.Page
import pages.chargeD._
import play.api.mvc.Call

class ChargeDNavigator @Inject()(val dataCacheConnector: UserAnswersCacheConnector) extends Navigator {

  def nextIndex(ua: UserAnswers, srn: String): Int = 0

  override protected def routeMap(ua: UserAnswers, srn: String): PartialFunction[Page, Call] = {
    case WhatYouWillNeedPage => MemberDetailsController.onPageLoad(NormalMode, srn, nextIndex(ua, srn))
    case MemberDetailsPage(index) => ChargeDetailsController.onPageLoad(NormalMode, srn, index)
    case ChargeDetailsPage(index) => CheckYourAnswersController.onPageLoad(srn, index)
    case CheckYourAnswersPage => controllers.routes.AFTSummaryController.onPageLoad(NormalMode, srn)
  }

  override protected def editRouteMap(ua: UserAnswers, srn: String): PartialFunction[Page, Call] = {
    case MemberDetailsPage(index) => CheckYourAnswersController.onPageLoad(srn, index)
    case ChargeDetailsPage(index) => CheckYourAnswersController.onPageLoad(srn, index)
  }
}
