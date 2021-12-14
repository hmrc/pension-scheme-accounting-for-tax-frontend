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
import models.LocalDateBinder._
import models.requests.DataRequest
import models.{AccessType, ChargeType, MemberDetails, NormalMode, UserAnswers}
import pages._
import play.api.mvc.{AnyContent, Call}
import services.{AFTService, ChargeDService, ChargeEService, ChargeGService}

import java.time.LocalDate

class ChargeNavigator @Inject()(config: FrontendAppConfig,
                                val dataCacheConnector: UserAnswersCacheConnector,
                                chargeDHelper: ChargeDService,
                                chargeEHelper: ChargeEService,
                                chargeGHelper: ChargeGService,
                                aftService: AFTService
                               ) extends Navigator {

  override protected def routeMap(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
                                 (implicit request: DataRequest[AnyContent]): PartialFunction[Page, Call] = {
    case ChargeTypePage                 => chargeTypeNavigation(ua, srn, startDate, accessType, version)
    case AFTSummaryPage                 => aftSummaryNavigation(ua, srn, startDate, accessType, version)
    case ConfirmSubmitAFTReturnPage     => confirmSubmitNavigation(ua, srn, startDate, accessType, version)
    case ConfirmSubmitAFTAmendmentPage  => confirmSubmitAmendmentNavigation(ua, srn, startDate, accessType, version)
    case DeclarationPage                => controllers.routes.ConfirmationController.onPageLoad(srn, startDate, accessType, version)
    case EnterPsaIdPage                 => controllers.routes.DeclarationController.onPageLoad(srn, startDate, accessType, version)
  }

  override protected def editRouteMap(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
                                     (implicit request: DataRequest[AnyContent]): PartialFunction[Page, Call] = {
    case ChargeTypePage => sessionExpiredPage
  }

  //scalastyle:off cyclomatic.complexity
  private def chargeTypeNavigation(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
                                 : Call =
    ua.get(ChargeTypePage) match {
      case Some(ChargeType.ChargeTypeShortService) =>
        controllers.chargeA.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, version)

      case Some(ChargeType.ChargeTypeLumpSumDeath) =>
        controllers.chargeB.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, version)

      case Some(ChargeType.ChargeTypeAuthSurplus)  =>
        controllers.chargeC.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, version)

      case Some(ChargeType.ChargeTypeAnnualAllowance) if nextIndexChargeE(ua) == 0 =>
        controllers.chargeE.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, version)

      case Some(ChargeType.ChargeTypeAnnualAllowance) =>
        controllers.chargeE.routes.MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, version,
          nextIndexChargeE(ua))

      case Some(ChargeType.ChargeTypeDeRegistration) =>
        controllers.chargeF.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, version)

      case Some(ChargeType.ChargeTypeLifetimeAllowance) if nextIndexChargeD(ua) == 0 =>
        controllers.chargeD.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, version)

      case Some(ChargeType.ChargeTypeLifetimeAllowance) =>
        controllers.chargeD.routes.MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, version,
          nextIndexChargeD(ua))

      case Some(ChargeType.ChargeTypeOverseasTransfer) if nextIndexChargeG(ua) == 0 =>
        controllers.chargeG.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, version)

      case Some(ChargeType.ChargeTypeOverseasTransfer) =>
        controllers.chargeG.routes.MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, version,
          nextIndexChargeG(ua))

      case _ => sessionExpiredPage
    }
  //scalastyle:on cyclomatic.complexity

  private def nextIndexChargeD(ua: UserAnswers) : Int =
    ua.getAllMembersInCharge[MemberDetails](charge = "chargeDDetails").size

  private def nextIndexChargeE(ua: UserAnswers) : Int =
    ua.getAllMembersInCharge[MemberDetails](charge = "chargeEDetails").size

  private def nextIndexChargeG(ua: UserAnswers) : Int =
    ua.getAllMembersInCharge[MemberDetails](charge = "chargeGDetails").size

  private def confirmSubmitNavigation(ua: UserAnswers, srn: String, startDate: LocalDate,
                                      accessType: AccessType, version: Int)(implicit request: DataRequest[AnyContent]) = {
    ua.get(ConfirmSubmitAFTReturnPage) match {
      case Some(true) =>
        (request.psaId, request.pspId) match {
          case (None, Some(_)) => controllers.routes.EnterPsaIdController.onPageLoad(srn, startDate, accessType, version)
          case (Some(_), None) => controllers.routes.DeclarationController.onPageLoad(srn, startDate, accessType, version)
          case _ =>  sessionExpiredPage
        }

      case Some(false) =>
        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version)
      case _ => sessionExpiredPage
    }
  }

  private def confirmSubmitAmendmentNavigation(ua: UserAnswers, srn: String, startDate: LocalDate,
                                      accessType: AccessType, version: Int)(implicit request: DataRequest[AnyContent]) =
        (request.psaId, request.pspId) match {
          case (None, Some(_)) => controllers.routes.EnterPsaIdController.onPageLoad(srn, startDate, accessType, version)
          case (Some(_), None) => controllers.routes.DeclarationController.onPageLoad(srn, startDate, accessType, version)
          case _ =>  sessionExpiredPage
        }

  private def aftSummaryNavigation(ua: UserAnswers, srn: String, startDate: LocalDate,
                                   accessType: AccessType, version: Int)(implicit request: DataRequest[AnyContent]): Call = {
    (ua.get(AFTSummaryPage), ua.get(QuarterPage)) match {
      case (Some(true), _) =>
        controllers.routes.ChargeTypeController.onPageLoad(srn, startDate, accessType, version)
      case (Some(false), Some(quarter)) =>
          if (aftService.isSubmissionDisabled(quarter.endDate)) {
            controllers.routes.NotSubmissionTimeController.onPageLoad(srn, startDate)
          } else {
            if (request.isAmendment) {
              controllers.amend.routes.ConfirmSubmitAFTAmendmentController.onPageLoad(srn, startDate, accessType, version)
            } else {
              controllers.routes.ConfirmSubmitAFTReturnController.onPageLoad(srn, startDate)
            }
          }
      case _ => sessionExpiredPage
    }
  }

  private val sessionExpiredPage = controllers.routes.SessionExpiredController.onPageLoad
}
