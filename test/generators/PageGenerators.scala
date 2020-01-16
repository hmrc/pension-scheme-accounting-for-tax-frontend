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

package generators

import org.scalacheck.Arbitrary
import pages._
import pages.chargeC._
import pages.chargeE.{AnnualAllowanceYearPage, DeleteMemberPage, MemberDetailsPage}
import pages.chargeF.ChargeDetailsPage

trait PageGenerators {

  implicit lazy val arbitraryChargeGDetailsPage: Arbitrary[pages.chargeG.ChargeDetailsPage.type] =
    Arbitrary(pages.chargeG.ChargeDetailsPage)

  implicit lazy val arbitraryChargeDetailsPage: Arbitrary[ChargeDetailsPage.type] =
    Arbitrary(ChargeDetailsPage)

  implicit lazy val arbitraryChargeCDetailsPage: Arbitrary[ChargeCDetailsPage.type] =
    Arbitrary(ChargeCDetailsPage)

  implicit lazy val arbitrarySponsoringIndividualDetailsPage: Arbitrary[SponsoringIndividualDetailsPage.type] =
    Arbitrary(SponsoringIndividualDetailsPage)

  implicit lazy val arbitrarySponsoringEmployerAddressPage: Arbitrary[SponsoringEmployerAddressPage.type] =
    Arbitrary(SponsoringEmployerAddressPage)

  implicit lazy val arbitrarySponsoringOrganisationDetailsPage: Arbitrary[SponsoringOrganisationDetailsPage.type] =
    Arbitrary(SponsoringOrganisationDetailsPage)

  implicit lazy val arbitraryIsSponsoringEmployerIndividualPage: Arbitrary[IsSponsoringEmployerIndividualPage.type] =
    Arbitrary(IsSponsoringEmployerIndividualPage)

  implicit lazy val arbitraryAFTSummaryPage: Arbitrary[AFTSummaryPage.type] =
    Arbitrary(AFTSummaryPage)

  implicit lazy val arbitraryDeleteMemberPage: Arbitrary[DeleteMemberPage.type] =
    Arbitrary(DeleteMemberPage)

  implicit lazy val arbitraryMemberDetailsPage: Arbitrary[MemberDetailsPage.type] =
    Arbitrary(MemberDetailsPage)

  implicit lazy val arbitraryChargeTypePage: Arbitrary[ChargeTypePage.type] =
    Arbitrary(ChargeTypePage)

  implicit lazy val arbitraryAnnualAllowanceYearPage: Arbitrary[AnnualAllowanceYearPage.type] =
    Arbitrary(AnnualAllowanceYearPage)
}
