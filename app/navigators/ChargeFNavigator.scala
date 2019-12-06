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
import models.{CheckMode, NormalMode, UserAnswers}
import pages.chargeF.{ChargeDetailsPage, WhatYouWillNeedPage}
import pages.Page
import play.api.mvc.Call

class ChargeFNavigator @Inject()(val dataCacheConnector: UserAnswersCacheConnector) extends Navigator {

  override protected def routeMap(ua: UserAnswers, srn: String): PartialFunction[Page, Call] = {
    case WhatYouWillNeedPage => controllers.chargeF.routes.ChargeDetailsController.onPageLoad(NormalMode, srn)
    case ChargeDetailsPage => controllers.chargeF.routes.CheckYourAnswersController.onPageLoad(srn)
  }

  override protected def editRouteMap(ua: UserAnswers, srn: String): PartialFunction[Page, Call] = {
    case ChargeDetailsPage => controllers.chargeF.routes.CheckYourAnswersController.onPageLoad(srn)
  }
}
