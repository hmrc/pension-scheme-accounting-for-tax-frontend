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

import controllers.chargeC.routes.{IsSponsoringEmployerIndividualController, SponsoringOrganisationDetailsController}
import models.{NormalMode, UserAnswers}
import org.scalatest.prop.TableFor3
import pages.Page
import pages.chargeC.{IsSponsoringEmployerIndividualPage, SponsoringOrganisationDetailsPage, WhatYouWillNeedPage}
import controllers.chargeC.routes.SponsoringEmployerAddressController
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
        row(SponsoringOrganisationDetailsPage)(SponsoringEmployerAddressController.onPageLoad(NormalMode, srn))
      )

    behave like navigatorWithRoutesForMode(NormalMode)(navigator, normalModeRoutes, srn)
  }
}

object ChargeCNavigatorSpec {

  private val srn = "test-srn"

  private val sponsoringEmployerIsOrganisation = UserAnswers(Json.obj(
    IsSponsoringEmployerIndividualPage.toString -> false
  ))
}
