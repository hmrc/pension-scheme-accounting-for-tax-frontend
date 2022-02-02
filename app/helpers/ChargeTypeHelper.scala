/*
 * Copyright 2022 HM Revenue & Customs
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

package helpers

import models.ChargeType
import models.ChargeType.{
  ChargeTypeAnnualAllowance,
  ChargeTypeAuthSurplus,
  ChargeTypeDeRegistration,
  ChargeTypeLifetimeAllowance,
  ChargeTypeLumpSumDeath,
  ChargeTypeOverseasTransfer,
  ChargeTypeShortService
}
import pages.Page
import pages.chargeA.{CheckYourAnswersPage => ShortServiceCYAPage}
import pages.chargeB.{CheckYourAnswersPage => LumpSumDeathCYAPage}
import pages.chargeC.{CheckYourAnswersPage => AuthSurplusCYAPage}
import pages.chargeD.{CheckYourAnswersPage => LifetimeAllowanceCYAPage}
import pages.chargeE.{CheckYourAnswersPage => AnnualAllowanceCYAPage}
import pages.chargeF.{CheckYourAnswersPage => DeRegistrationCYAPage}
import pages.chargeG.{CheckYourAnswersPage => OverseasTransferCYAPage}

case object ChargeTypeHelper {

  def getCheckYourAnswersPage(chargeType: ChargeType): Page = {
    chargeType match {
      case ChargeTypeAnnualAllowance   => AnnualAllowanceCYAPage
      case ChargeTypeLifetimeAllowance => LifetimeAllowanceCYAPage
      case ChargeTypeOverseasTransfer  => OverseasTransferCYAPage
      case ChargeTypeAuthSurplus       => AuthSurplusCYAPage
      case ChargeTypeDeRegistration    => DeRegistrationCYAPage
      case ChargeTypeShortService      => ShortServiceCYAPage
      case ChargeTypeLumpSumDeath      => LumpSumDeathCYAPage
    }
  }

}