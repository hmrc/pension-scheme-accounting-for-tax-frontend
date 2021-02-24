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

import java.time.LocalDate
import com.google.inject.Inject
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import models.LocalDateBinder._
import models.requests.DataRequest
import models.{UserAnswers, ChargeType, NormalMode, AccessType}
import pages._
import play.api.mvc.{Call, AnyContent}
import services.{ChargeEService, ChargeDService, ChargeGService, AFTService}

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
                                  (implicit request: DataRequest[AnyContent]): Call =
    ua.get(ChargeTypePage) match {
      case Some(ChargeType.ChargeTypeShortService) =>
        controllers.chargeA.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, version)

      case Some(ChargeType.ChargeTypeLumpSumDeath) =>
        controllers.chargeB.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, version)

      case Some(ChargeType.ChargeTypeAuthSurplus)  =>
        controllers.chargeC.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, version)

      case Some(ChargeType.ChargeTypeAnnualAllowance) if nextIndexChargeE(ua, srn, startDate, accessType, version) == 0 =>
        controllers.chargeE.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, version)

      case Some(ChargeType.ChargeTypeAnnualAllowance) =>
        controllers.chargeE.routes.MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, version,
          nextIndexChargeE(ua, srn, startDate, accessType, version))

      case Some(ChargeType.ChargeTypeDeRegistration) =>
        controllers.chargeF.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, version)

      case Some(ChargeType.ChargeTypeLifetimeAllowance) if nextIndexChargeD(ua, srn, startDate, accessType, version) == 0 =>
        controllers.chargeD.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, version)

      case Some(ChargeType.ChargeTypeLifetimeAllowance) =>
        controllers.chargeD.routes.MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, version,
          nextIndexChargeD(ua, srn, startDate, accessType, version))

      case Some(ChargeType.ChargeTypeOverseasTransfer) if nextIndexChargeG(ua, srn, startDate, accessType, version) == 0 =>
        controllers.chargeG.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, version)

      case Some(ChargeType.ChargeTypeOverseasTransfer) =>
        controllers.chargeG.routes.MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, version,
          nextIndexChargeG(ua, srn, startDate, accessType, version))

      case _ => sessionExpiredPage
    }
  //scalastyle:on cyclomatic.complexity

  def nextIndexChargeD(ua: UserAnswers, srn: String, startDate: LocalDate,
                       accessType: AccessType, version: Int)(implicit request: DataRequest[AnyContent]): Int =
    chargeDHelper.getLifetimeAllowanceMembers(ua, srn, startDate, accessType, version).size

  def nextIndexChargeE(ua: UserAnswers, srn: String, startDate: LocalDate,
                       accessType: AccessType, version: Int)(implicit request: DataRequest[AnyContent]): Int =
    chargeEHelper.getAnnualAllowanceMembers(ua, srn, startDate, accessType, version).size

  def nextIndexChargeG(ua: UserAnswers, srn: String, startDate: LocalDate,
                       accessType: AccessType, version: Int)(implicit request: DataRequest[AnyContent]): Int =
    chargeGHelper.getOverseasTransferMembers(ua, srn, startDate, accessType, version).size

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
            Call("GET", config.managePensionsSchemeSummaryUrl.format(srn))
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

  private val sessionExpiredPage = controllers.routes.SessionExpiredController.onPageLoad()
}
