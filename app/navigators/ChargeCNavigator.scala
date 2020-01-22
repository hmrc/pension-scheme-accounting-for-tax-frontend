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
import controllers.chargeC.routes._
import models.{CheckMode, NormalMode, UserAnswers}
import pages.Page
import pages.chargeC._
import play.api.mvc.Call
import controllers.chargeC.routes._
import services.ChargeCService._

class ChargeCNavigator @Inject()(val dataCacheConnector: UserAnswersCacheConnector) extends Navigator {

  def nextIndex(ua: UserAnswers, srn: String): Int = getSponsoringEmployersIncludingDeleted(ua, srn).size

  def addEmployers(ua: UserAnswers, srn: String): Call = ua.get(AddEmployersPage) match {
    case Some(true) => IsSponsoringEmployerIndividualController.onPageLoad(NormalMode, srn, nextIndex(ua, srn))
    case _ => controllers.routes.AFTSummaryController.onPageLoad(srn, None)
  }

  override protected def routeMap(ua: UserAnswers, srn: String): PartialFunction[Page, Call] = {

      case WhatYouWillNeedPage => IsSponsoringEmployerIndividualController.onPageLoad(NormalMode, srn, nextIndex(ua, srn))
      case IsSponsoringEmployerIndividualPage(index) if optionIsSponsoringEmployerIndividual(index, ua).contains(false) =>
        SponsoringOrganisationDetailsController.onPageLoad(NormalMode, srn, index)
      case IsSponsoringEmployerIndividualPage(index) if optionIsSponsoringEmployerIndividual(index, ua).contains(true) =>
        SponsoringIndividualDetailsController.onPageLoad(NormalMode, srn, index)
      case SponsoringOrganisationDetailsPage(index) => SponsoringEmployerAddressController.onPageLoad(NormalMode, srn, index)
      case SponsoringIndividualDetailsPage(index) => SponsoringEmployerAddressController.onPageLoad(NormalMode, srn, index)
      case SponsoringEmployerAddressPage(index) => ChargeDetailsController.onPageLoad(NormalMode, srn, index)
      case ChargeCDetailsPage(index) => CheckYourAnswersController.onPageLoad(srn, index)
      case CheckYourAnswersPage => AddEmployersController.onPageLoad(srn)
      case AddEmployersPage => addEmployers(ua, srn)
      case DeleteEmployerPage if getSponsoringEmployers(ua, srn).nonEmpty => AddEmployersController.onPageLoad(srn)
      case DeleteEmployerPage => controllers.routes.AFTSummaryController.onPageLoad(srn, None)

  }

  override protected def editRouteMap(ua: UserAnswers, srn: String): PartialFunction[Page, Call] = {
    case IsSponsoringEmployerIndividualPage(index) => editRoutesForIsSponsoringEmployerIndividualPage(index, ua, srn)
    case SponsoringOrganisationDetailsPage(index) => editRoutesForSponsoringEmployerPages(index, ua, srn)
    case SponsoringIndividualDetailsPage(index) => editRoutesForSponsoringEmployerPages(index, ua, srn)
    case SponsoringEmployerAddressPage(index) => CheckYourAnswersController.onPageLoad(srn, index)
    case ChargeCDetailsPage(index) => CheckYourAnswersController.onPageLoad(srn, index)
  }

  private def optionIsSponsoringEmployerIndividual(index: Int, ua: UserAnswers):Option[Boolean] = ua.get(IsSponsoringEmployerIndividualPage(index))

  private def editRoutesForIsSponsoringEmployerIndividualPage(index: Int, ua:UserAnswers, srn: String):Call = {
    (ua.get(IsSponsoringEmployerIndividualPage(index)), ua.get(SponsoringIndividualDetailsPage(index)), ua.get(SponsoringOrganisationDetailsPage(index))) match {
      case (Some(false), _, None) => SponsoringOrganisationDetailsController.onPageLoad(CheckMode, srn, index)
      case (Some(false), _, _) => CheckYourAnswersController.onPageLoad(srn, index)
      case (Some(true), None, _) => SponsoringIndividualDetailsController.onPageLoad(CheckMode, srn, index)
      case _ => CheckYourAnswersController.onPageLoad(srn, index)
    }
  }

  private def editRoutesForSponsoringEmployerPages(index: Int, ua:UserAnswers, srn: String):Call = {
    ua.get(SponsoringEmployerAddressPage(index)) match {
      case Some(_) => CheckYourAnswersController.onPageLoad(srn, index)
      case _ => SponsoringEmployerAddressController.onPageLoad(CheckMode, srn, index)
    }
  }

}
