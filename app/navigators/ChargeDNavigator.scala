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
import controllers.chargeD.routes._
import controllers.routes
import helpers.{ChargeServiceHelper, DeleteChargeHelper}
import models.ChargeType.ChargeTypeLifetimeAllowance
import models.Index._
import models.LocalDateBinder._
import models.fileUpload.InputSelection.{FileUploadInput, ManualInput}
import models.requests.DataRequest
import models.{AccessType, CheckMode, MemberDetails, Mode, NormalMode, UploadId, UserAnswers}
import pages.chargeD._
import pages.fileUpload.{FileUploadPage, InputSelectionPage}
import pages.mccloud._
import pages.{IsPublicServicePensionsRemedyPage, Page, QuarterPage}
import play.api.libs.json.JsArray
import play.api.mvc.{AnyContent, Call}

import java.time.LocalDate

class ChargeDNavigator @Inject()(val dataCacheConnector: UserAnswersCacheConnector,
                                 deleteChargeHelper: DeleteChargeHelper,
                                 chargeServiceHelper: ChargeServiceHelper,
                                 config: FrontendAppConfig)
  extends Navigator {

  private def nextIndex(ua: UserAnswers): Int =
    ua.getAllMembersInCharge[MemberDetails](charge = "chargeDDetails").size

  private def addMembers(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Call = {
    val showPSRQuestions = enablePSR(ua)
    val addMembersPageVal = ua.get(AddMembersPage)

    (addMembersPageVal, showPSRQuestions) match {
      case (Some(true), true) =>
        routes.IsPublicServicePensionsRemedyController
          .onPageLoad(ChargeTypeLifetimeAllowance, NormalMode, srn, startDate, accessType, version, Some(nextIndex(ua)))
      case (Some(true), false) =>
        MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, version, nextIndex(ua))
      case _ => controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version)
    }
  }

  private def deleteMemberRoutes(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)(
    implicit request: DataRequest[AnyContent]): Call =
    if (deleteChargeHelper.allChargesDeletedOrZeroed(ua) && !request.isAmendment) {
      Call("GET", config.managePensionsSchemeSummaryUrl.format(srn))
    } else if (chargeServiceHelper.isEmployerOrMemberPresent(ua, "chargeDDetails")) {
      AddMembersController.onPageLoad(srn, startDate, accessType, version)
    } else {
      controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version)
    }

  //scalastyle:off method.length
  //scalastyle:off cyclomatic.complexity
  override protected def routeMap(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)(
    implicit request: DataRequest[AnyContent]): PartialFunction[Page, Call] = {
    case WhatYouWillNeedPage => MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, version, nextIndex(ua))

    case InputSelectionPage(ChargeTypeLifetimeAllowance) => inputSelectionNav(ua, srn, startDate, accessType, version, nextIndex(ua))

    case pages.fileUpload.WhatYouWillNeedPage(ChargeTypeLifetimeAllowance) =>
      controllers.fileUpload.routes.FileUploadController.onPageLoad(srn, startDate, accessType, version, ChargeTypeLifetimeAllowance)
    case FileUploadPage(ChargeTypeLifetimeAllowance) =>
      controllers.fileUpload.routes.ValidationController.onPageLoad(srn, startDate, accessType, version, ChargeTypeLifetimeAllowance, UploadId(""))

    case MemberDetailsPage(index) => ChargeDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, version, index)

    case ChargeDetailsPage(index) => ua.get(IsPublicServicePensionsRemedyPage(ChargeTypeLifetimeAllowance, Some(index))) match {
      case Some(true) =>
        controllers.mccloud.routes.IsChargeInAdditionReportedController
          .onPageLoad(ChargeTypeLifetimeAllowance, NormalMode, srn, startDate, accessType, version, index)
      case _ => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
    }

    case IsPublicServicePensionsRemedyPage(ChargeTypeLifetimeAllowance, index) =>
      val previousValue = request.userAnswers.get(IsPublicServicePensionsRemedyPage(ChargeTypeLifetimeAllowance, index))
      routeFromIsPublicServicePensionsRemedyPage(ua, NormalMode, srn, startDate, accessType, version, index, previousValue)

    case IsChargeInAdditionReportedPage(ChargeTypeLifetimeAllowance, index) =>
      routeFromIsChargeInAdditionReportedPage(ua, NormalMode, srn, startDate, accessType, version, index)

    case WasAnotherPensionSchemePage(ChargeTypeLifetimeAllowance, index) =>
      routeFromWasAnotherPensionSchemePage(ua, NormalMode, srn, startDate, accessType, version, index)

    case EnterPstrPage(ChargeTypeLifetimeAllowance, index, schemeIndex) =>
      controllers.mccloud.routes.TaxYearReportedAndPaidController
        .onPageLoad(ChargeTypeLifetimeAllowance, NormalMode, srn, startDate, accessType, version, index, Some(schemeIndex))

    case TaxYearReportedAndPaidPage(ChargeTypeLifetimeAllowance, index, schemeIndex) =>
      controllers.mccloud.routes.TaxQuarterReportedAndPaidController
        .onPageLoad(ChargeTypeLifetimeAllowance, NormalMode, srn, startDate, accessType, version, index, schemeIndex)
    case TaxQuarterReportedAndPaidPage(ChargeTypeLifetimeAllowance, index, schemeIndex) =>
      controllers.mccloud.routes.ChargeAmountReportedController
        .onPageLoad(ChargeTypeLifetimeAllowance, NormalMode, srn, startDate, accessType, version, index, schemeIndex)
    case ChargeAmountReportedPage(ChargeTypeLifetimeAllowance, index, schemeIndex) =>
      routeFromChargeAmountReportedPage(ua, NormalMode, srn, startDate, accessType, version, index, schemeIndex)

    case AddAnotherPensionSchemePage(ChargeTypeLifetimeAllowance, index, schemeIndex) =>
      routeFromAddAnotherPensionSchemePage(ua, NormalMode, srn, startDate, accessType, version, index, schemeIndex)

    case CheckYourAnswersPage => AddMembersController.onPageLoad(srn, startDate, accessType, version)
    case AddMembersPage => addMembers(ua, srn, startDate, accessType, version)
    case DeleteMemberPage => deleteMemberRoutes(ua, srn, startDate, accessType, version)
  }

  private def countSchemeSize(userAnswers: UserAnswers, index: Int): Int = {
    SchemePathHelper
      .path(ChargeTypeLifetimeAllowance, index)
      .readNullable[JsArray]
      .reads(userAnswers.data)
      .asOpt
      .flatten
      .map(_.value.map { item =>
        (item \ "chargeAmountReported").asOpt[BigDecimal]
      }.count(_.isDefined)
      ).getOrElse(0)
  }

  private def routeFromIsPublicServicePensionsRemedyPage(userAnswers: UserAnswers,
                                                         mode: Mode,
                                                         srn: String,
                                                         startDate: LocalDate,
                                                         accessType: AccessType,
                                                         version: Int,
                                                         optIndex: Option[Int],
                                                         previousValue: Option[Boolean]): Call = {
    mode match {
      case NormalMode =>
        (userAnswers.get(InputSelectionPage(ChargeTypeLifetimeAllowance)), userAnswers.get(AddMembersPage), optIndex) match {
          case (_, Some(true), Some(index)) =>
            MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, version, index)
          case (Some(FileUploadInput), _, None) =>
            controllers.fileUpload.routes.WhatYouWillNeedController
              .onPageLoad(srn, startDate, accessType, version, ChargeTypeLifetimeAllowance)
          case (Some(ManualInput), _, Some(index)) =>
            controllers.chargeD.routes.WhatYouWillNeedController
              .onPageLoad(srn, startDate, accessType, version, index)
          case _ => sessionExpiredPage
        }
      case CheckMode =>
        (previousValue, userAnswers.get(IsPublicServicePensionsRemedyPage(ChargeTypeLifetimeAllowance, optIndex)), optIndex) match {
          case (Some(pv), Some(cv), Some(index)) if cv == pv => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
          case (_, Some(true), Some(index)) =>
            controllers.mccloud.routes.IsChargeInAdditionReportedController
              .onPageLoad(ChargeTypeLifetimeAllowance, mode, srn, startDate, accessType, version, index)
          case (_, Some(false), Some(index)) => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
          case _ => sessionExpiredPage
        }
      case _ => sessionExpiredPage
    }
  }

  private def routeFromIsChargeInAdditionReportedPage(userAnswers: UserAnswers,
                                                      mode: Mode,
                                                      srn: String,
                                                      startDate: LocalDate,
                                                      accessType: AccessType,
                                                      version: Int,
                                                      index: Int): Call = {
    userAnswers.get(IsChargeInAdditionReportedPage(ChargeTypeLifetimeAllowance, index)) match {
      case Some(true) =>
        controllers.mccloud.routes.WasAnotherPensionSchemeController
          .onPageLoad(ChargeTypeLifetimeAllowance, mode, srn, startDate, accessType, version, index)
      case Some(false) => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
      case _ => sessionExpiredPage
    }
  }

  private def routeFromWasAnotherPensionSchemePage(userAnswers: UserAnswers,
                                                   mode: Mode,
                                                   srn: String,
                                                   startDate: LocalDate,
                                                   accessType: AccessType,
                                                   version: Int,
                                                   index: Int): Call = {
    userAnswers.get(WasAnotherPensionSchemePage(ChargeTypeLifetimeAllowance, index)) match {
      case Some(true) =>
        controllers.mccloud.routes.EnterPstrController
          .onPageLoad(ChargeTypeLifetimeAllowance, mode, srn, startDate, accessType, version, index, countSchemeSize(userAnswers, index))
      case Some(false) =>
        controllers.mccloud.routes.TaxYearReportedAndPaidController
          .onPageLoad(ChargeTypeLifetimeAllowance, mode, srn, startDate, accessType, version, index, None)
      case _ => sessionExpiredPage
    }
  }

  private def routeFromEnterPstrPage(userAnswers: UserAnswers,
                                     mode: Mode,
                                     srn: String,
                                     startDate: LocalDate,
                                     accessType: AccessType,
                                     version: Int,
                                     index: Int,
                                     schemeIndex: Int): Call = {

    val taxYearReported = userAnswers.get(TaxYearReportedAndPaidPage(ChargeTypeLifetimeAllowance, index, Some(schemeIndex)))
    taxYearReported match {
      case Some(_) => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
      case _ =>
        controllers.mccloud.routes.TaxYearReportedAndPaidController
          .onPageLoad(ChargeTypeLifetimeAllowance, mode, srn, startDate, accessType, version, index, Some(schemeIndex))
    }
  }

  private def routeFromTaxQuarterReportedAndPaidPage(userAnswers: UserAnswers,
                                                     mode: Mode,
                                                     srn: String,
                                                     startDate: LocalDate,
                                                     accessType: AccessType,
                                                     version: Int,
                                                     index: Int,
                                                     schemeIndex: Option[Int]): Call = {
    val chargeAmount = userAnswers.get(ChargeAmountReportedPage(ChargeTypeLifetimeAllowance, index, schemeIndex))
    chargeAmount match {
      case Some(_) => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
      case _ =>
        controllers.mccloud.routes.ChargeAmountReportedController
          .onPageLoad(ChargeTypeLifetimeAllowance, mode, srn, startDate, accessType, version, index, schemeIndex)
    }
  }

  private def routeFromChargeAmountReportedPage(userAnswers: UserAnswers,
                                                mode: Mode,
                                                srn: String,
                                                startDate: LocalDate,
                                                accessType: AccessType,
                                                version: Int,
                                                index: Int,
                                                schemeIndex: Option[Int]): Call = {
    val schemeSize = countSchemeSize(userAnswers, index)
    val schemeSizeLessThan5 = schemeSize > 0 && schemeSize < 5
    (schemeIndex, schemeSizeLessThan5, mode) match {
      case (Some(i), true, NormalMode) =>
        controllers.mccloud.routes.AddAnotherPensionSchemeController
          .onPageLoad(ChargeTypeLifetimeAllowance, mode, srn, startDate, accessType, version, index, i)
      case (Some(i), true, CheckMode) => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
      case (Some(i), false, _) => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
      case (None, true | false, _) => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
      case (_, _, _) => sessionExpiredPage
    }
  }

  private def routeFromAddAnotherPensionSchemePage(userAnswers: UserAnswers,
                                                   mode: Mode,
                                                   srn: String,
                                                   startDate: LocalDate,
                                                   accessType: AccessType,
                                                   version: Int,
                                                   index: Int,
                                                   schemeIndex: Int): Call = {
    userAnswers.get(AddAnotherPensionSchemePage(ChargeTypeLifetimeAllowance, index, schemeIndex)) match {
      case Some(true) =>
        controllers.mccloud.routes.EnterPstrController
          .onPageLoad(ChargeTypeLifetimeAllowance, mode, srn, startDate, accessType, version, index, countSchemeSize(userAnswers, index))
      case Some(false) => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
      case _ => sessionExpiredPage
    }
  }

  private def routeFromRemovePensionSchemePage(userAnswers: UserAnswers,
                                               mode: Mode,
                                               srn: String,
                                               startDate: LocalDate,
                                               accessType: AccessType,
                                               version: Int,
                                               index: Int,
                                               schemeIndex: Int): Call = {
    val schemeSize = countSchemeSize(userAnswers, index)
    val schemeSizeLessThan5 = schemeSize > 1 && schemeSize <= 5

    (userAnswers.get(RemovePensionSchemePage(ChargeTypeLifetimeAllowance, index, schemeIndex)), schemeSizeLessThan5) match {
      case (Some(true), false) => controllers.mccloud.routes.WasAnotherPensionSchemeController
        .onPageLoad(ChargeTypeLifetimeAllowance, mode, srn, startDate, accessType, version, index)
      case (Some(true), true) => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
      case (Some(false), true | false) => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
      case (_, _) => sessionExpiredPage
    }
  }

  private def inputSelectionNav(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Int): Call = {
    val inputSelection = ua.get(InputSelectionPage(ChargeTypeLifetimeAllowance))
    val showPSRQuestions = enablePSR(ua)

    (inputSelection, showPSRQuestions) match {
      case (Some(ManualInput), true) => controllers.routes.IsPublicServicePensionsRemedyController
        .onPageLoad(ChargeTypeLifetimeAllowance, NormalMode, srn, startDate, accessType, version, Some(nextIndex(ua)))
      case (Some(FileUploadInput), true) => controllers.routes.IsPublicServicePensionsRemedyController
        .onPageLoad(ChargeTypeLifetimeAllowance, NormalMode, srn, startDate, accessType, version, None)
      case (Some(ManualInput), false) => controllers.chargeD.routes.WhatYouWillNeedController
        .onPageLoad(srn, startDate, accessType, version, index)
      case (Some(FileUploadInput), false) => controllers.fileUpload.routes.WhatYouWillNeedController
        .onPageLoad(srn, startDate, accessType, version, ChargeTypeLifetimeAllowance)
      case _ => sessionExpiredPage
    }
  }

  private def enablePSR(userAnswers: UserAnswers): Boolean = {
    val selectedAFTQuarter = userAnswers.get(QuarterPage)
    selectedAFTQuarter match {
      case Some(aftQuarter) =>
        val mcCloudDisplayFromDate = config.mccloudPsrStartDate
        aftQuarter.startDate.isAfter(mcCloudDisplayFromDate) || aftQuarter.startDate.isEqual(mcCloudDisplayFromDate)
      case _ => false
    }
  }

  override protected def editRouteMap(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)(
    implicit request: DataRequest[AnyContent]): PartialFunction[Page, Call] = {
    case MemberDetailsPage(index) => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
    case ChargeDetailsPage(index) => CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)
    case IsPublicServicePensionsRemedyPage(ChargeTypeLifetimeAllowance, index) =>
      val previousValue = request.userAnswers.get(IsPublicServicePensionsRemedyPage(ChargeTypeLifetimeAllowance, index))
      routeFromIsPublicServicePensionsRemedyPage(ua, CheckMode, srn, startDate, accessType, version, index, previousValue)
    case IsChargeInAdditionReportedPage(ChargeTypeLifetimeAllowance, index) =>
      routeFromIsChargeInAdditionReportedPage(ua, CheckMode, srn, startDate, accessType, version, index)
    case WasAnotherPensionSchemePage(ChargeTypeLifetimeAllowance, index) =>
      routeFromWasAnotherPensionSchemePage(ua, CheckMode, srn, startDate, accessType, version, index)
    case EnterPstrPage(ChargeTypeLifetimeAllowance, index, schemeIndex) =>
      routeFromEnterPstrPage(ua, CheckMode, srn, startDate, accessType, version, index, schemeIndex)
    case TaxYearReportedAndPaidPage(ChargeTypeLifetimeAllowance, index, schemeIndex) =>
      controllers.mccloud.routes.TaxQuarterReportedAndPaidController
        .onPageLoad(ChargeTypeLifetimeAllowance, CheckMode, srn, startDate, accessType, version, index, schemeIndex)
    case TaxQuarterReportedAndPaidPage(ChargeTypeLifetimeAllowance, index, schemeIndex) =>
      routeFromTaxQuarterReportedAndPaidPage(ua, CheckMode, srn, startDate, accessType, version, index, schemeIndex)
    case ChargeAmountReportedPage(ChargeTypeLifetimeAllowance, index, schemeIndex) =>
      routeFromChargeAmountReportedPage(ua, CheckMode, srn, startDate, accessType, version, index, schemeIndex)
    case AddAnotherPensionSchemePage(ChargeTypeLifetimeAllowance, index, schemeIndex) =>
      routeFromAddAnotherPensionSchemePage(ua, CheckMode, srn, startDate, accessType, version, index, schemeIndex)
    case RemovePensionSchemePage(ChargeTypeLifetimeAllowance, index, schemeIndex) =>
      routeFromRemovePensionSchemePage(ua, CheckMode, srn, startDate, accessType, version, index, schemeIndex)
  }

  private val sessionExpiredPage = controllers.routes.SessionExpiredController.onPageLoad
}
