/*
 * Copyright 2019 HM Revenue & Customs
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
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}

trait ModelGenerators {

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

  implicit lazy val arbitraryYearRange: Arbitrary[YearRange] =
    Arbitrary {
      Gen.oneOf(YearRange.values.toSeq)
    }
}
