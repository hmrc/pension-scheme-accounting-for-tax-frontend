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

import controllers.chargeC.routes._
import data.SampleData
import models.{CheckMode, NormalMode, UserAnswers}
import org.scalatest.prop.TableFor3
import pages.Page
import pages.chargeC._
import play.api.libs.json.Json
import play.api.mvc.Call

class ChargeCNavigatorSpec extends NavigatorBehaviour {
  private val navigator: CompoundNavigator = injector.instanceOf[CompoundNavigator]
  private val index = 0
  import ChargeCNavigatorSpec._

  "NormalMode" must {
    def normalModeRoutes: TableFor3[Page, UserAnswers, Call] =
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(WhatYouWillNeedPage)(IsSponsoringEmployerIndividualController.onPageLoad(NormalMode, srn, index)),
        row(IsSponsoringEmployerIndividualPage(index))(SponsoringOrganisationDetailsController.onPageLoad(NormalMode, srn, index), Some(sponsoringEmployerIsOrganisation)),
        row(IsSponsoringEmployerIndividualPage(index))(SponsoringIndividualDetailsController.onPageLoad(NormalMode, srn, index), Some(sponsoringEmployerIsIndividual)),
        row(SponsoringOrganisationDetailsPage(index))(SponsoringEmployerAddressController.onPageLoad(NormalMode, srn, index)),
        row(SponsoringIndividualDetailsPage(index))(SponsoringEmployerAddressController.onPageLoad(NormalMode, srn, index)),
        row(SponsoringEmployerAddressPage(index))(ChargeDetailsController.onPageLoad(NormalMode, srn, index)),
        row(ChargeCDetailsPage(index))(CheckYourAnswersController.onPageLoad(srn, index)),
        row(CheckYourAnswersPage)(controllers.routes.AFTSummaryController.onPageLoad(NormalMode, srn)),
        row(AddEmployersPage)(IsSponsoringEmployerIndividualController.onPageLoad(NormalMode, srn, index), addEmployersYes),
        row(AddEmployersPage)(controllers.routes.AFTSummaryController.onPageLoad(NormalMode, srn), addEmployersNo),
        row(DeleteEmployerPage)(controllers.routes.AFTSummaryController.onPageLoad(NormalMode, srn)),
        row(DeleteEmployerPage)(AddEmployersController.onPageLoad(srn), Some(SampleData.chargeGMember))
      )

    behave like navigatorWithRoutesForMode(NormalMode)(navigator, normalModeRoutes, srn)
  }

  "CheckMode" must {
    def checkModeRoutes: TableFor3[Page, UserAnswers, Call] =
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(IsSponsoringEmployerIndividualPage(index))(SponsoringOrganisationDetailsController.onPageLoad(CheckMode, srn, index), Some(sponsoringEmployerIsOrganisation)),
        row(IsSponsoringEmployerIndividualPage(index))(CheckYourAnswersController.onPageLoad(srn, index), Some(sponsoringEmployerIsOrganisationWithOrganisationDetails)),
        row(IsSponsoringEmployerIndividualPage(index))(SponsoringIndividualDetailsController.onPageLoad(CheckMode, srn, index), Some(sponsoringEmployerIsIndividual)),
        row(IsSponsoringEmployerIndividualPage(index))(CheckYourAnswersController.onPageLoad(srn, index), Some(sponsoringEmployerIsIndividualWithIndividualDetails)),
        row(SponsoringOrganisationDetailsPage(index))(CheckYourAnswersController.onPageLoad(srn, index), Some(sponsoringEmployerAddress)),
        row(SponsoringOrganisationDetailsPage(index))(SponsoringEmployerAddressController.onPageLoad(CheckMode, srn, index)),
        row(SponsoringIndividualDetailsPage(index))(CheckYourAnswersController.onPageLoad(srn, index), Some(sponsoringEmployerAddress)),
        row(SponsoringIndividualDetailsPage(index))(SponsoringEmployerAddressController.onPageLoad(CheckMode, srn, index)),
        row(SponsoringEmployerAddressPage(index))(CheckYourAnswersController.onPageLoad(srn, index)),
        row(ChargeCDetailsPage(index))(CheckYourAnswersController.onPageLoad(srn, index)),
        row(CheckYourAnswersPage)(controllers.routes.AFTSummaryController.onPageLoad(CheckMode, srn))
      )

    behave like navigatorWithRoutesForMode(CheckMode)(navigator, checkModeRoutes, srn)
  }
}

object ChargeCNavigatorSpec {

  private val srn = "test-srn"


  private val addEmployersYes = UserAnswers().set(AddEmployersPage, true).toOption
  private val addEmployersNo = UserAnswers().set(AddEmployersPage, false).toOption

  private val sponsoringEmployerIsOrganisation = UserAnswers(Json.obj(
    "chargeCDetails" -> Json.obj(
      "employers" -> Json.arr(Json.obj(
      IsSponsoringEmployerIndividualPage.toString -> false
      ))
    )
  ))

  private val sponsoringEmployerIsIndividual = UserAnswers(Json.obj(
    "chargeCDetails" -> Json.obj(
      "employers" -> Json.arr(Json.obj(
      IsSponsoringEmployerIndividualPage.toString -> true
      ))
    )
  ))

  private val sponsoringEmployerIsOrganisationWithOrganisationDetails = UserAnswers(Json.obj(
    "chargeCDetails" -> Json.obj(
      "employers" -> Json.arr(Json.obj(
      IsSponsoringEmployerIndividualPage.toString -> false,
      SponsoringOrganisationDetailsPage.toString -> SampleData.sponsoringOrganisationDetails
      ))
    )
  ))

  private val sponsoringEmployerIsIndividualWithIndividualDetails = UserAnswers(Json.obj(
    "chargeCDetails" -> Json.obj(
      "employers" -> Json.arr(Json.obj(
      IsSponsoringEmployerIndividualPage.toString -> true,
      SponsoringIndividualDetailsPage.toString -> SampleData.sponsoringIndividualDetails
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
}
