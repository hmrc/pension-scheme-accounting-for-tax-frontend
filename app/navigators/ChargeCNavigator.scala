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
import models.{CheckMode, NormalMode, UserAnswers}
import pages.{Page, VersionQuery}
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
      case ChargeCDetailsPage => CheckYourAnswersController.onPageLoad(srn)
      case CheckYourAnswersPage => controllers.routes.AFTSummaryController.onPageLoad(srn, ua.get(VersionQuery))
    }
  }

  override protected def editRouteMap(ua: UserAnswers, srn: String): PartialFunction[Page, Call] = {
    case IsSponsoringEmployerIndividualPage => editRoutesForIsSponsoringEmployerIndividualPage(ua, srn)
    case SponsoringOrganisationDetailsPage => editRoutesForSponsoringEmployerPages(ua, srn)
    case SponsoringIndividualDetailsPage => editRoutesForSponsoringEmployerPages(ua, srn)
    case SponsoringEmployerAddressPage => CheckYourAnswersController.onPageLoad(srn)
    case ChargeCDetailsPage => CheckYourAnswersController.onPageLoad(srn)
  }

  private def editRoutesForIsSponsoringEmployerIndividualPage(ua:UserAnswers, srn: String):Call = {
    (ua.get(IsSponsoringEmployerIndividualPage), ua.get(SponsoringIndividualDetailsPage), ua.get(SponsoringOrganisationDetailsPage)) match {
      case (Some(false), _, None) => SponsoringOrganisationDetailsController.onPageLoad(CheckMode, srn)
      case (Some(false), _, _) => CheckYourAnswersController.onPageLoad(srn)
      case (Some(true), None, _) => SponsoringIndividualDetailsController.onPageLoad(CheckMode, srn)
      case _ => CheckYourAnswersController.onPageLoad(srn)
    }
  }

  private def editRoutesForSponsoringEmployerPages(ua:UserAnswers, srn: String):Call = {
    ua.get(SponsoringEmployerAddressPage) match {
      case Some(_) => CheckYourAnswersController.onPageLoad(srn)
      case _ => SponsoringEmployerAddressController.onPageLoad(CheckMode, srn)
    }
  }

}
