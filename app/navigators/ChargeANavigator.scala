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
import models.{Draft, NormalMode, UserAnswers}
import pages.Page
import pages.chargeA.{ChargeDetailsPage, CheckYourAnswersPage, DeleteChargePage, WhatYouWillNeedPage}
import play.api.mvc.{AnyContent, Call}
import java.time.LocalDate

import config.FrontendAppConfig
import helpers.DeleteChargeHelper
import models.LocalDateBinder._
import models.requests.DataRequest
class ChargeANavigator @Inject()(val dataCacheConnector: UserAnswersCacheConnector,
                                 deleteChargeHelper: DeleteChargeHelper, config: FrontendAppConfig) extends Navigator {

  override protected def routeMap(ua: UserAnswers, srn: String, startDate: LocalDate)
                                 (implicit request: DataRequest[AnyContent]): PartialFunction[Page, Call] = {
    case WhatYouWillNeedPage  => controllers.chargeA.routes.ChargeDetailsController.onPageLoad(NormalMode, srn, startDate)
    case ChargeDetailsPage    => controllers.chargeA.routes.CheckYourAnswersController.onPageLoad(srn, startDate)
    case CheckYourAnswersPage => controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, Draft, 1)
    case DeleteChargePage if deleteChargeHelper.allChargesDeletedOrZeroed(ua) && !request.isAmendment =>
      Call("GET", config.managePensionsSchemeSummaryUrl.format(srn))
    case DeleteChargePage =>
      controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, Draft, 1)
  }

  override protected def editRouteMap(ua: UserAnswers, srn: String, startDate: LocalDate)
                                     (implicit request: DataRequest[AnyContent]): PartialFunction[Page, Call] = {
    case ChargeDetailsPage => controllers.chargeA.routes.CheckYourAnswersController.onPageLoad(srn, startDate)
  }
}
