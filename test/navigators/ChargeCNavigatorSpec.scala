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

import config.FrontendAppConfig
import controllers.chargeC.routes._
import data.SampleData
import data.SampleData.{accessType, versionInt}
import models.LocalDateBinder._
import models.{CheckMode, NormalMode, SponsoringEmployerType, UserAnswers}
import org.scalatest.prop.TableFor3
import pages.chargeC._
import pages.{Page, chargeA, chargeB}
import play.api.libs.json.Json
import play.api.mvc.Call
import utils.AFTConstants.QUARTER_START_DATE

class ChargeCNavigatorSpec extends NavigatorBehaviour {
  private val navigator: CompoundNavigator = injector.instanceOf[CompoundNavigator]
  private def config: FrontendAppConfig = injector.instanceOf[FrontendAppConfig]
  private val index = 0
  import ChargeCNavigatorSpec._

  "NormalMode" must {
    def normalModeRoutes: TableFor3[Page, UserAnswers, Call] =
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(WhatYouWillNeedPage)(WhichTypeOfSponsoringEmployerController.onPageLoad(NormalMode,srn, startDate, accessType, versionInt, index)),
        row(WhichTypeOfSponsoringEmployerPage(index))(
          SponsoringOrganisationDetailsController.onPageLoad(NormalMode,srn, startDate, accessType, versionInt, index), Some(sponsoringEmployerIsOrganisation)),
        row(WhichTypeOfSponsoringEmployerPage(index))(
          SponsoringIndividualDetailsController.onPageLoad(NormalMode,srn, startDate, accessType, versionInt, index), Some(sponsoringEmployerIsIndividual)),
        row(SponsoringOrganisationDetailsPage(index))(
          SponsoringEmployerAddressSearchController.onPageLoad(NormalMode,srn, startDate, accessType, versionInt, index)),
        row(SponsoringIndividualDetailsPage(index))(
          SponsoringEmployerAddressSearchController.onPageLoad(NormalMode,srn, startDate, accessType, versionInt, index)),
        row(SponsoringEmployerAddressSearchPage(index))(
          SponsoringEmployerAddressResultsController.onPageLoad(NormalMode,srn, startDate, accessType, versionInt, index)),
        row(SponsoringEmployerAddressResultsPage(index))(
          ChargeDetailsController.onPageLoad(NormalMode,srn, startDate, accessType, versionInt, index)),
        row(SponsoringEmployerAddressPage(index))(
          ChargeDetailsController.onPageLoad(NormalMode,srn, startDate, accessType, versionInt, index)),
        row(ChargeCDetailsPage(index))(
          CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt, index)),
        row(CheckYourAnswersPage)(
          AddEmployersController.onPageLoad(srn, startDate, accessType, versionInt)),
        row(AddEmployersPage)(
          WhichTypeOfSponsoringEmployerController.onPageLoad(NormalMode,srn, startDate, accessType, versionInt, index), addEmployersYes),
        row(AddEmployersPage)(
          controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, versionInt), addEmployersNo),
        row(DeleteEmployerPage)(
          Call("GET",config.managePensionsSchemeSummaryUrl.format(srn)), zeroedCharge),
        row(DeleteEmployerPage)(
          controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, versionInt), multipleCharges),
        row(DeleteEmployerPage)(
          AddEmployersController.onPageLoad(srn, startDate, accessType, versionInt), Some(SampleData.chargeCEmployer))
      )

    behave like navigatorWithRoutesForMode(NormalMode)(navigator, normalModeRoutes,srn, startDate, accessType, versionInt)
  }

  "CheckMode" must {
    def checkModeRoutes: TableFor3[Page, UserAnswers, Call] =
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(WhichTypeOfSponsoringEmployerPage(index))(
          SponsoringOrganisationDetailsController.onPageLoad(CheckMode,srn, startDate, accessType, versionInt, index), Some(sponsoringEmployerIsOrganisation)),
        row(WhichTypeOfSponsoringEmployerPage(index))(
          SponsoringIndividualDetailsController.onPageLoad(CheckMode,srn, startDate, accessType, versionInt, index), Some(sponsoringEmployerIsIndividual)),
        row(SponsoringOrganisationDetailsPage(index))(
          CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt, index), Some(sponsoringEmployerAddress)),
        row(SponsoringOrganisationDetailsPage(index))(
          SponsoringEmployerAddressController.onPageLoad(CheckMode,srn, startDate, accessType, versionInt, index)),
        row(SponsoringIndividualDetailsPage(index))(
          CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt, index), Some(sponsoringEmployerAddress)),
        row(SponsoringIndividualDetailsPage(index))(
          SponsoringEmployerAddressController.onPageLoad(CheckMode,srn, startDate, accessType, versionInt, index)),
        row(SponsoringEmployerAddressPage(index))(
          CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt, index), Some(chargeCDetails)),
        row(SponsoringEmployerAddressPage(index))(
          ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, versionInt, index)),
        row(ChargeCDetailsPage(index))(
          CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt, index))
      )

    behave like navigatorWithRoutesForMode(CheckMode)(navigator, checkModeRoutes,srn, startDate, accessType, versionInt)
  }
}

object ChargeCNavigatorSpec {

  private val srn = "test-srn"
  private val startDate = QUARTER_START_DATE

  private val addEmployersYes = UserAnswers().set(AddEmployersPage, true).toOption
  private val addEmployersNo = UserAnswers().set(AddEmployersPage, false).toOption

  private val sponsoringEmployerIsOrganisation = UserAnswers(Json.obj(
    "chargeCDetails" -> Json.obj(
      "employers" -> Json.arr(Json.obj(
        WhichTypeOfSponsoringEmployerPage.toString -> SponsoringEmployerType.SponsoringEmployerTypeOrganisation.toString
      ))
    )
  ))

  private val sponsoringEmployerIsIndividual = UserAnswers(Json.obj(
    "chargeCDetails" -> Json.obj(
      "employers" -> Json.arr(Json.obj(
        WhichTypeOfSponsoringEmployerPage.toString -> SponsoringEmployerType.SponsoringEmployerTypeIndividual.toString
      ))
    )
  ))

  private val sponsoringEmployerAddress = UserAnswers(Json.obj(
    "chargeCDetails" -> Json.obj(
      "employers" -> Json.arr(Json.obj(
        SponsoringEmployerAddressPage.toString -> SampleData.sponsoringEmployerAddress
      ))
    )
  ))

  private val chargeCDetails = UserAnswers(Json.obj(
    "chargeCDetails" -> Json.obj(
      "employers" -> Json.arr(Json.obj(
        ChargeCDetailsPage.toString -> SampleData.chargeCDetails
      ))
    )
  ))

  private val zeroedCharge = UserAnswers().set(chargeA.ChargeDetailsPage,
    SampleData.chargeAChargeDetails.copy(totalAmount = BigDecimal(0.00))).toOption
  private val multipleCharges = UserAnswers().set(chargeA.ChargeDetailsPage, SampleData.chargeAChargeDetails)
    .flatMap(_.set(chargeB.ChargeBDetailsPage, SampleData.chargeBDetails)).toOption
}
