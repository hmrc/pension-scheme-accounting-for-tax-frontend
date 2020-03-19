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

import models._
import models.chargeC.ChargeCDetails
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import pages._
import pages.chargeC._
import pages.chargeE.{DeleteMemberPage, MemberDetailsPage}
import pages.chargeF.ChargeDetailsPage
import play.api.libs.json.{JsValue, Json}

trait UserAnswersEntryGenerators extends PageGenerators with ModelGenerators {

  implicit lazy val arbitraryConfirmSubmitAFTReturnUserAnswersEntry: Arbitrary[(ConfirmSubmitAFTReturnPage.type, JsValue)] =
    Arbitrary {
      for {
        page  <- arbitrary[ConfirmSubmitAFTReturnPage.type]
        value <- arbitrary[Boolean].map(Json.toJson(_))
      } yield (page, value)
    }

  implicit lazy val arbitraryChargeDetailsUserAnswersEntry: Arbitrary[(ChargeDetailsPage.type, JsValue)] =
    Arbitrary {
      for {
        page  <- arbitrary[ChargeDetailsPage.type]
        value <- arbitrary[Int].map(Json.toJson(_))
      } yield (page, value)
    }

  implicit lazy val arbitrarySponsoringIndividualDetailsUserAnswersEntry: Arbitrary[(SponsoringIndividualDetailsPage.type, JsValue)] =
    Arbitrary {
      for {
        page  <- arbitrary[SponsoringIndividualDetailsPage.type]
        value <- arbitrary[String].suchThat(_.nonEmpty).map(Json.toJson(_))
      } yield (page, value)
    }

  implicit lazy val arbitrarySponsoringEmployerAddressUserAnswersEntry: Arbitrary[(SponsoringEmployerAddressPage.type, JsValue)] =
    Arbitrary {
      for {
        page  <- arbitrary[SponsoringEmployerAddressPage.type]
        value <- arbitrary[String].suchThat(_.nonEmpty).map(Json.toJson(_))
      } yield (page, value)
    }

  implicit lazy val arbitrarySponsoringOrganisationDetailsUserAnswersEntry: Arbitrary[(SponsoringOrganisationDetailsPage.type, JsValue)] =
    Arbitrary {
      for {
        page  <- arbitrary[SponsoringOrganisationDetailsPage.type]
        value <- arbitrary[String].suchThat(_.nonEmpty).map(Json.toJson(_))
      } yield (page, value)
    }

  implicit lazy val arbitraryIsSponsoringEmployerIndividualUserAnswersEntry: Arbitrary[(WhichTypeOfSponsoringEmployerPage.type, JsValue)] =
    Arbitrary {
      for {
        page  <- arbitrary[WhichTypeOfSponsoringEmployerPage.type]
        value <- arbitrary[Boolean].map(Json.toJson(_))
      } yield (page, value)
    }

  implicit lazy val arbitraryAFTSummaryUserAnswersEntry: Arbitrary[(AFTSummaryPage.type, JsValue)] =
    Arbitrary {
      for {
        page  <- arbitrary[AFTSummaryPage.type]
        value <- arbitrary[Boolean].map(Json.toJson(_))
      } yield (page, value)
    }

  implicit lazy val arbitraryDeleteMemberUserAnswersEntry: Arbitrary[(DeleteMemberPage.type, JsValue)] =
    Arbitrary {
      for {
        page  <- arbitrary[DeleteMemberPage.type]
        value <- arbitrary[Boolean].map(Json.toJson(_))
      } yield (page, value)
    }

  implicit lazy val arbitraryMemberDetailsUserAnswersEntry: Arbitrary[(MemberDetailsPage.type, JsValue)] =
    Arbitrary {
      for {
        page  <- arbitrary[MemberDetailsPage.type]
        value <- arbitrary[MemberDetails].map(Json.toJson(_))
      } yield (page, value)
    }

  implicit lazy val arbitraryChargeTypeUserAnswersEntry: Arbitrary[(ChargeTypePage.type, JsValue)] =
    Arbitrary {
      for {
        page  <- arbitrary[ChargeTypePage.type]
        value <- arbitrary[ChargeType].map(Json.toJson(_))
      } yield (page, value)
    }

  implicit lazy val arbitraryChargeGDetailsUserAnswersEntry: Arbitrary[(pages.chargeG.ChargeDetailsPage.type, JsValue)] =
    Arbitrary {
      for {
        page  <- arbitrary[pages.chargeG.ChargeDetailsPage.type]
        value <- arbitrary[Int].map(Json.toJson(_))
      } yield (page, value)
    }
}
