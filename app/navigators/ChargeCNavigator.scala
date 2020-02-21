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
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import models.{CheckMode, NormalMode, UserAnswers}
import pages.Page
import pages.chargeC._
import play.api.mvc.Call
import controllers.chargeC.routes._
import services.ChargeCService._
import services.AFTReturnTidyService
import java.time.LocalDate
import models.LocalDateBinder._

class ChargeCNavigator @Inject()(val dataCacheConnector: UserAnswersCacheConnector,
                                 aftReturnTidyService: AFTReturnTidyService,
                                 config: FrontendAppConfig) extends Navigator {

  def nextIndex(ua: UserAnswers, srn: String, startDate: LocalDate): Int = getSponsoringEmployersIncludingDeleted(ua, srn, startDate).size

  def addEmployers(ua: UserAnswers, srn: String, startDate: LocalDate): Call = ua.get(AddEmployersPage) match {
    case Some(true) => IsSponsoringEmployerIndividualController.onPageLoad(NormalMode, srn, startDate, nextIndex(ua, srn, startDate))
    case _ => controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, None)
  }

  //scalastyle:off cyclomatic.complexity
  override protected def routeMap(ua: UserAnswers, srn: String, startDate: LocalDate): PartialFunction[Page, Call] = {
    case WhatYouWillNeedPage =>
      IsSponsoringEmployerIndividualController.onPageLoad(NormalMode, srn, startDate, nextIndex(ua, srn, startDate))

    case IsSponsoringEmployerIndividualPage(index) if isIndividualOrOrg(index, ua).contains(false) =>
      SponsoringOrganisationDetailsController.onPageLoad(NormalMode, srn, startDate, index)

    case IsSponsoringEmployerIndividualPage(index) if isIndividualOrOrg(index, ua).contains(true) =>
      SponsoringIndividualDetailsController.onPageLoad(NormalMode, srn, startDate, index)

    case SponsoringOrganisationDetailsPage(index) =>
      SponsoringEmployerAddressController.onPageLoad(NormalMode, srn, startDate, index)

    case SponsoringIndividualDetailsPage(index) =>
      SponsoringEmployerAddressController.onPageLoad(NormalMode, srn, startDate, index)

    case SponsoringEmployerAddressPage(index) =>
      ChargeDetailsController.onPageLoad(NormalMode, srn, startDate, index)

    case ChargeCDetailsPage(index) =>
      CheckYourAnswersController.onPageLoad(srn, startDate, index)

    case CheckYourAnswersPage =>
      AddEmployersController.onPageLoad(srn, startDate)

    case AddEmployersPage =>
      addEmployers(ua, srn, startDate)

    case DeleteEmployerPage if getSponsoringEmployers(ua, srn, startDate).nonEmpty =>
      AddEmployersController.onPageLoad(srn, startDate)

    case DeleteEmployerPage if aftReturnTidyService.isAtLeastOneValidCharge(ua) =>
      controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, None)

    case DeleteEmployerPage =>
      Call("GET", config.managePensionsSchemeSummaryUrl.format(srn))
  }

  //scalastyle:on cyclomatic.complexity

  override protected def editRouteMap(ua: UserAnswers, srn: String, startDate: LocalDate): PartialFunction[Page, Call] = {
    case IsSponsoringEmployerIndividualPage(index) if isIndividualOrOrg(index, ua).contains(false) =>
      SponsoringOrganisationDetailsController.onPageLoad(CheckMode, srn, startDate, index)

    case IsSponsoringEmployerIndividualPage(index) if isIndividualOrOrg(index, ua).contains(true) =>
      SponsoringIndividualDetailsController.onPageLoad(CheckMode, srn, startDate, index)

    case SponsoringOrganisationDetailsPage(index) =>
      editRoutesForSponsoringEmployerPages(index, ua, srn, startDate)

    case SponsoringIndividualDetailsPage(index) =>
      editRoutesForSponsoringEmployerPages(index, ua, srn, startDate)

    case SponsoringEmployerAddressPage(index) =>
      editRoutesForSponsoringEmployerAddress(index, ua, srn, startDate)

    case ChargeCDetailsPage(index) =>
      CheckYourAnswersController.onPageLoad(srn, startDate, index)
  }

  private def isIndividualOrOrg(index: Int, ua: UserAnswers): Option[Boolean] = ua.get(IsSponsoringEmployerIndividualPage(index))

  private def editRoutesForSponsoringEmployerPages(index: Int, ua: UserAnswers, srn: String, startDate: LocalDate): Call = {
    ua.get(SponsoringEmployerAddressPage(index)) match {
      case Some(_) => CheckYourAnswersController.onPageLoad(srn, startDate, index)
      case _ => SponsoringEmployerAddressController.onPageLoad(CheckMode, srn, startDate, index)
    }
  }

  private def editRoutesForSponsoringEmployerAddress(index: Int, ua: UserAnswers, srn: String, startDate: LocalDate): Call = {
    ua.get(ChargeCDetailsPage(index)) match {
      case Some(_) => CheckYourAnswersController.onPageLoad(srn, startDate, index)
      case _ => ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, index)
    }
  }

}
