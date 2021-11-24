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
import helpers.DeleteChargeHelper
import models.LocalDateBinder._
import models.requests.DataRequest
import models.{AccessType, NormalMode, UserAnswers}
import pages.Page
import pages.chargeB.{ChargeBDetailsPage, CheckYourAnswersPage, DeleteChargePage, WhatYouWillNeedPage}
import play.api.mvc.{AnyContent, Call}

import java.time.LocalDate
class ChargeBNavigator @Inject()(val dataCacheConnector: UserAnswersCacheConnector,
                                 deleteChargeHelper: DeleteChargeHelper, config: FrontendAppConfig) extends Navigator {

  override protected def routeMap(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
                                 (implicit request: DataRequest[AnyContent]): PartialFunction[Page, Call] = {
    case WhatYouWillNeedPage  => controllers.chargeB.routes.ChargeDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, version)
    case ChargeBDetailsPage   => controllers.chargeB.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version)
    case CheckYourAnswersPage => controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version)
    case DeleteChargePage if deleteChargeHelper.allChargesDeletedOrZeroed(ua) && !request.isAmendment =>
      Call("GET", config.managePensionsSchemeSummaryUrl.format(srn))
    case DeleteChargePage =>
      controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version)
  }

  override protected def editRouteMap(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
                                     (implicit request: DataRequest[AnyContent]): PartialFunction[Page, Call] = {
    case ChargeBDetailsPage => controllers.chargeB.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version)
  }
}
