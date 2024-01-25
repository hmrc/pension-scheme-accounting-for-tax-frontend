/*
 * Copyright 2024 HM Revenue & Customs
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
import controllers.chargeC.routes._
import helpers.{ChargeServiceHelper, DeleteChargeHelper}
import models.LocalDateBinder._
import models.SponsoringEmployerType._
import models.requests.DataRequest
import models.{AccessType, CheckMode, NormalMode, UserAnswers}
import pages.Page
import pages.chargeC._
import play.api.mvc.{AnyContent, Call}
import services.ChargeCService

import java.time.LocalDate
class ChargeCNavigator @Inject()(val dataCacheConnector: UserAnswersCacheConnector,
                                 deleteChargeHelper: DeleteChargeHelper,
                                 chargeCHelper: ChargeCService,
                                 chargeServiceHelper: ChargeServiceHelper,
                                 config: FrontendAppConfig)
  extends Navigator {

  def nextIndex(ua: UserAnswers): Int =
    chargeCHelper.numberOfEmployersIncludingDeleted(ua)

  def addEmployers(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
                  : Call = ua.get(AddEmployersPage) match {
    case Some(true) => WhichTypeOfSponsoringEmployerController.onPageLoad(NormalMode, srn, startDate, accessType, version,
      nextIndex(ua))
    case _          => controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version)
  }

  //scalastyle:off cyclomatic.complexity
  override protected def routeMap(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
                                 (implicit request: DataRequest[AnyContent]): PartialFunction[Page, Call] = {
    case WhatYouWillNeedPage =>
      WhichTypeOfSponsoringEmployerController.onPageLoad(NormalMode, srn, startDate, accessType, version,
        nextIndex(ua))

    case WhichTypeOfSponsoringEmployerPage(index) if ua.get(WhichTypeOfSponsoringEmployerPage(index)).contains(SponsoringEmployerTypeOrganisation) =>
      SponsoringOrganisationDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, version, index)

    case WhichTypeOfSponsoringEmployerPage(index) if ua.get(WhichTypeOfSponsoringEmployerPage(index)).contains(SponsoringEmployerTypeIndividual) =>
      SponsoringIndividualDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, version, index)

    case SponsoringOrganisationDetailsPage(index) =>
      SponsoringEmployerAddressSearchController.onPageLoad(NormalMode, srn, startDate, accessType, version, index)

    case SponsoringIndividualDetailsPage(index) =>
      SponsoringEmployerAddressSearchController.onPageLoad(NormalMode, srn, startDate, accessType, version, index)

    case SponsoringEmployerAddressSearchPage(index) =>
      SponsoringEmployerAddressResultsController.onPageLoad(NormalMode, srn, startDate, accessType, version, index)

    case SponsoringEmployerAddressResultsPage(index) =>
      ChargeDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, version, index)

    case SponsoringEmployerAddressPage(index) =>
      ChargeDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, version, index)

    case ChargeCDetailsPage(index) =>
      CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)

    case CheckYourAnswersPage =>
      AddEmployersController.onPageLoad(srn, startDate, accessType, version)

    case AddEmployersPage =>
      addEmployers(ua, srn, startDate, accessType, version)

    case DeleteEmployerPage if deleteChargeHelper.allChargesDeletedOrZeroed(ua) && !request.isAmendment =>
      Call("GET", config.managePensionsSchemeSummaryUrl.format(srn))

    case DeleteEmployerPage if chargeServiceHelper.isEmployerOrMemberPresent(ua, "chargeCDetails") =>
      AddEmployersController.onPageLoad(srn, startDate, accessType, version)

    case DeleteEmployerPage => controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version)
  }

  //scalastyle:on cyclomatic.complexity

  override protected def editRouteMap(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
                                     (implicit request: DataRequest[AnyContent]): PartialFunction[Page, Call] = {
    case WhichTypeOfSponsoringEmployerPage(index) if ua.get(WhichTypeOfSponsoringEmployerPage(index)).contains(SponsoringEmployerTypeOrganisation) =>
      SponsoringOrganisationDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index)

    case WhichTypeOfSponsoringEmployerPage(index) if ua.get(WhichTypeOfSponsoringEmployerPage(index)).contains(SponsoringEmployerTypeIndividual) =>
      SponsoringIndividualDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index)

    case SponsoringOrganisationDetailsPage(index) =>
      editRoutesForSponsoringEmployerPages(index, ua, srn, startDate, accessType, version)

    case SponsoringIndividualDetailsPage(index) =>
      editRoutesForSponsoringEmployerPages(index, ua, srn, startDate, accessType, version)

    case SponsoringEmployerAddressPage(index) =>
      editRoutesForSponsoringEmployerAddress(index, ua, srn, startDate, accessType, version)

    case ChargeCDetailsPage(index) =>
      CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
  }

  private def editRoutesForSponsoringEmployerPages(index: Int, ua: UserAnswers, srn: String, startDate: LocalDate,
                                                   accessType: AccessType, version: Int): Call = {
    ua.get(SponsoringEmployerAddressPage(index)) match {
      case Some(_) => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
      case _       => SponsoringEmployerAddressController.onPageLoad(CheckMode, srn, startDate, accessType, version, index)
    }
  }

  private def editRoutesForSponsoringEmployerAddress(index: Int, ua: UserAnswers, srn: String, startDate: LocalDate,
                                                     accessType: AccessType, version: Int): Call = {
    ua.get(ChargeCDetailsPage(index)) match {
      case Some(_) => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
      case _       => ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index)
    }
  }

}
