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

package generators

import models._
import models.chargeC.{ChargeCDetails, SponsoringEmployerAddress, SponsoringOrganisationDetails}
import models.chargeF.ChargeDetails
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}

import java.time.{Instant, LocalDate, ZoneOffset}

trait ModelGenerators {



  implicit lazy val arbitraryTolerantAddress: Arbitrary[TolerantAddress] =
    Arbitrary {
      for {
        addressLine1 <- arbitrary[Option[String]]
        addressLine2 <- arbitrary[Option[String]]
        townOrCity <- arbitrary[Option[String]]
        county <- arbitrary[Option[String]]
        postcode <- arbitrary[Option[String]]
        country <- arbitrary[Option[String]]
      } yield TolerantAddress(addressLine1, addressLine2, townOrCity, county, postcode, country)
    }

  implicit lazy val arbitraryMemberDetails: Arbitrary[MemberDetails] =
    Arbitrary {
      for {
        firstName <- arbitrary[String]
        lastName <- arbitrary[String]
        nino <- arbitrary[String]
      } yield MemberDetails(firstName, lastName, nino)
    }

  implicit lazy val arbitraryChargeType: Arbitrary[ChargeType] =
    Arbitrary {
      Gen.oneOf(ChargeType.values)
    }

  implicit lazy val arbitrarySponsoringEmployerAddress: Arbitrary[SponsoringEmployerAddress] =
    Arbitrary {
      for {
        line1 <- arbitrary[String]
        line2 <- arbitrary[String]
        townOrCity <- arbitrary[String]
        county <- arbitrary[String]
        country <- arbitrary[String]
        postcode <- arbitrary[String]
      } yield SponsoringEmployerAddress(line1,Some(line2),townOrCity,Some(county),country,Some(postcode))
    }

  implicit lazy val arbitrarySponsoringOrganisationDetails: Arbitrary[SponsoringOrganisationDetails] =
    Arbitrary {
      for {
        name <- arbitrary[String]
        crn <- arbitrary[String]
      } yield SponsoringOrganisationDetails(name,crn)
    }

  implicit lazy val arbitraryYearRange: Arbitrary[YearRange] =
    Arbitrary {
      Gen.oneOf(YearRange.values.toSeq)
    }

  def datesBetween(min: LocalDate, max: LocalDate): Gen[LocalDate] = {

    def toMillis(date: LocalDate): Long =
      date.atStartOfDay.atZone(ZoneOffset.UTC).toInstant.toEpochMilli

    Gen.choose(toMillis(min), toMillis(max)).map {
      millis =>
        Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toLocalDate
    }
  }

  implicit lazy val arbitraryLocalDate: Arbitrary[LocalDate] = Arbitrary {
    datesBetween(LocalDate.of(1900, 1, 1), LocalDate.of(2100, 1, 1))
  }

  implicit lazy val arbitraryChargeCDetails: Arbitrary[ChargeCDetails] =
    Arbitrary {
      for {
        paymentDate <- arbitraryLocalDate.arbitrary
        amountTaxDue <- arbitrary[BigDecimal]
      } yield ChargeCDetails(paymentDate, amountTaxDue)
    }

  implicit lazy val arbitraryChargeFDetails: Arbitrary[ChargeDetails] =
    Arbitrary {
      for {
        deregDate <- arbitraryLocalDate.arbitrary
        amountTaxDue <- arbitrary[BigDecimal]
      } yield ChargeDetails(deregDate, amountTaxDue)
    }
}
