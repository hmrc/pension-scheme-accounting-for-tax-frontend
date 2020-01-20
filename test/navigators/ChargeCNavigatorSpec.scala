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

  import ChargeCNavigatorSpec._

  "NormalMode" must {
    def normalModeRoutes: TableFor3[Page, UserAnswers, Call] =
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(WhatYouWillNeedPage)(IsSponsoringEmployerIndividualController.onPageLoad(NormalMode, srn)),
        row(IsSponsoringEmployerIndividualPage)(SponsoringOrganisationDetailsController.onPageLoad(NormalMode, srn), Some(sponsoringEmployerIsOrganisation)),
        row(IsSponsoringEmployerIndividualPage)(SponsoringIndividualDetailsController.onPageLoad(NormalMode, srn), Some(sponsoringEmployerIsIndividual)),
        row(SponsoringOrganisationDetailsPage)(SponsoringEmployerAddressController.onPageLoad(NormalMode, srn)),
        row(SponsoringIndividualDetailsPage)(SponsoringEmployerAddressController.onPageLoad(NormalMode, srn)),
        row(SponsoringEmployerAddressPage)(ChargeDetailsController.onPageLoad(NormalMode, srn)),
        row(ChargeCDetailsPage)(CheckYourAnswersController.onPageLoad(srn)),
        row(CheckYourAnswersPage)(controllers.routes.AFTSummaryController.onPageLoad(NormalMode, srn, None))
      )

    behave like navigatorWithRoutesForMode(NormalMode)(navigator, normalModeRoutes, srn)
  }

  "CheckMode" must {
    def checkModeRoutes: TableFor3[Page, UserAnswers, Call] =
      Table(
        ("Id", "UserAnswers", "Next Page"),
        row(IsSponsoringEmployerIndividualPage)(SponsoringOrganisationDetailsController.onPageLoad(CheckMode, srn), Some(sponsoringEmployerIsOrganisation)),
        row(IsSponsoringEmployerIndividualPage)(CheckYourAnswersController.onPageLoad(srn), Some(sponsoringEmployerIsOrganisationWithOrganisationDetails)),
        row(IsSponsoringEmployerIndividualPage)(SponsoringIndividualDetailsController.onPageLoad(CheckMode, srn), Some(sponsoringEmployerIsIndividual)),
        row(IsSponsoringEmployerIndividualPage)(CheckYourAnswersController.onPageLoad(srn), Some(sponsoringEmployerIsIndividualWithIndividualDetails)),
        row(SponsoringOrganisationDetailsPage)(CheckYourAnswersController.onPageLoad(srn), Some(sponsoringEmployerAddress)),
        row(SponsoringOrganisationDetailsPage)(SponsoringEmployerAddressController.onPageLoad(CheckMode, srn)),
        row(SponsoringIndividualDetailsPage)(CheckYourAnswersController.onPageLoad(srn), Some(sponsoringEmployerAddress)),
        row(SponsoringIndividualDetailsPage)(SponsoringEmployerAddressController.onPageLoad(CheckMode, srn)),
        row(SponsoringEmployerAddressPage)(CheckYourAnswersController.onPageLoad(srn)),
        row(ChargeCDetailsPage)(CheckYourAnswersController.onPageLoad(srn)),
        row(CheckYourAnswersPage)(controllers.routes.AFTSummaryController.onPageLoad(CheckMode, srn, None))
      )

    behave like navigatorWithRoutesForMode(CheckMode)(navigator, checkModeRoutes, srn)
  }
}

object ChargeCNavigatorSpec {

  private val srn = "test-srn"

  private val sponsoringEmployerIsOrganisation = UserAnswers(Json.obj(
    "chargeCDetails" -> Json.obj(
      IsSponsoringEmployerIndividualPage.toString -> false
    )
  ))

  private val sponsoringEmployerIsIndividual = UserAnswers(Json.obj(
    "chargeCDetails" -> Json.obj(
      IsSponsoringEmployerIndividualPage.toString -> true
    )
  ))

  private val sponsoringEmployerIsOrganisationWithOrganisationDetails = UserAnswers(Json.obj(
    "chargeCDetails" -> Json.obj(
      IsSponsoringEmployerIndividualPage.toString -> false,
      SponsoringOrganisationDetailsPage.toString -> SampleData.sponsoringOrganisationDetails
    )
  ))

  private val sponsoringEmployerIsIndividualWithIndividualDetails = UserAnswers(Json.obj(
    "chargeCDetails" -> Json.obj(
      IsSponsoringEmployerIndividualPage.toString -> true,
      SponsoringIndividualDetailsPage.toString -> SampleData.sponsoringIndividualDetails
    )
  ))

  private val sponsoringEmployerAddress = UserAnswers(Json.obj(
    "chargeCDetails" -> Json.obj(
      SponsoringEmployerAddressPage.toString -> SampleData.sponsoringEmployerAddress
    )
  ))
}
