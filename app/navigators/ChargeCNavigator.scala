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
import pages.chargeC._
import play.api.mvc.Call
import controllers.chargeC.routes._

class ChargeCNavigator @Inject()(val dataCacheConnector: UserAnswersCacheConnector) extends Navigator {

  override protected def routeMap(ua: UserAnswers, srn: String): PartialFunction[Page, Call] = {
    lazy val optionIsSponsoringEmployerIndividual:Option[Boolean] = ua.get(IsSponsoringEmployerIndividualPage)

    {
      case WhatYouWillNeedPage => IsSponsoringEmployerIndividualController.onPageLoad(NormalMode, srn)
      case IsSponsoringEmployerIndividualPage if optionIsSponsoringEmployerIndividual.contains(false) =>
        SponsoringOrganisationDetailsController.onPageLoad(NormalMode, srn)
      case IsSponsoringEmployerIndividualPage if optionIsSponsoringEmployerIndividual.contains(true) =>
        SponsoringIndividualDetailsController.onPageLoad(NormalMode, srn)
      case SponsoringOrganisationDetailsPage => SponsoringEmployerAddressController.onPageLoad(NormalMode, srn)
      case SponsoringIndividualDetailsPage => SponsoringEmployerAddressController.onPageLoad(NormalMode, srn)
      case SponsoringEmployerAddressPage => ChargeDetailsController.onPageLoad(NormalMode, srn)
    }
  }

  override protected def editRouteMap(ua: UserAnswers, srn: String): PartialFunction[Page, Call] = {
    case WhatYouWillNeedPage => controllers.routes.IndexController.onPageLoad()
  }
}
