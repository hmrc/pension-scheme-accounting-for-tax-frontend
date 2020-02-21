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
import models.{NormalMode, UserAnswers}
import pages.Page
import pages.chargeB.{ChargeBDetailsPage, CheckYourAnswersPage, WhatYouWillNeedPage}
import play.api.mvc.Call
import java.time.LocalDate
import models.LocalDateBinder._

class ChargeBNavigator @Inject()(val dataCacheConnector: UserAnswersCacheConnector) extends Navigator {

  override protected def routeMap(ua: UserAnswers, srn: String, startDate: LocalDate): PartialFunction[Page, Call] = {
    case WhatYouWillNeedPage => controllers.chargeB.routes.ChargeDetailsController.onPageLoad(NormalMode, srn, startDate)
    case ChargeBDetailsPage => controllers.chargeB.routes.CheckYourAnswersController.onPageLoad(srn, startDate)
    case CheckYourAnswersPage => controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, None)
  }

  override protected def editRouteMap(ua: UserAnswers, srn: String, startDate: LocalDate): PartialFunction[Page, Call] = {
    case ChargeBDetailsPage => controllers.chargeB.routes.CheckYourAnswersController.onPageLoad(srn, startDate)
  }
}
