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

package pages.chargeC

import models.SponsoringEmployerType.{SponsoringEmployerTypeIndividual, SponsoringEmployerTypeOrganisation}
import models.chargeC.{ChargeCDetails, SponsoringEmployerAddress, SponsoringOrganisationDetails}
import models.{MemberDetails, UserAnswers}
import pages.behaviours.PageBehaviours

import java.time.LocalDate

class WhichTypeOfSponsoringEmployerPageSpec extends PageBehaviours {

  val ua: UserAnswers = UserAnswers().set(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeOrganisation).
    flatMap(_.set(SponsoringOrganisationDetailsPage(0), SponsoringOrganisationDetails("name", "srn"))).
    flatMap(_.set(SponsoringIndividualDetailsPage(0), MemberDetails("first", "last", "ab200100a")))
    .flatMap(_.set(SponsoringEmployerAddressPage(0), SponsoringEmployerAddress("line1", "line2", Some("town"), None, "GB", None)))
    .flatMap(_.set(ChargeCDetailsPage(0), ChargeCDetails(LocalDate.now(), 10.00))).getOrElse(UserAnswers())

  "WhichTypeOfSponsoringEmployerPage" - {
    "must clean up the data for organisation if employer type is individual" in {
      val result = ua.set(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeIndividual).getOrElse(UserAnswers())

      result.get(SponsoringOrganisationDetailsPage(0)) mustNot be(defined)
      result.get(SponsoringEmployerAddressPage(0)) mustNot be(defined)
      result.get(ChargeCDetailsPage(0)) mustNot be(defined)

      result.get(SponsoringIndividualDetailsPage(0)) must be(defined)
    }

    "must clean up the data for individual if employer type is organisation" in {
      val result = ua.set(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeOrganisation).getOrElse(UserAnswers())

      result.get(SponsoringIndividualDetailsPage(0)) mustNot be(defined)
      result.get(SponsoringEmployerAddressPage(0)) mustNot be(defined)
      result.get(ChargeCDetailsPage(0)) mustNot be(defined)

      result.get(SponsoringOrganisationDetailsPage(0)) must be(defined)
    }
  }
}
