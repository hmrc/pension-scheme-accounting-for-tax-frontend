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
import models.ChargeType.{ChargeTypeAnnualAllowance, ChargeTypeAuthSurplus, ChargeTypeDeRegistration, ChargeTypeLifetimeAllowance, ChargeTypeLumpSumDeath, ChargeTypeOverseasTransfer, ChargeTypeShortService}
import pages.chargeA.{CheckYourAnswersPage => ShortServiceCYAPage}
import pages.chargeB.{CheckYourAnswersPage => LumpSumDeathCYAPage}
import pages.chargeC.{CheckYourAnswersPage => AuthSurplusCYAPage, TotalChargeAmountPage => AuthSurplusTotalChargeAmount}
import pages.chargeD.{CheckYourAnswersPage => LifetimeAllowanceCYAPage, TotalChargeAmountPage => LifetimeAllowanceTotalChargeAmount}
import pages.chargeE.{CheckYourAnswersPage => AnnualAllowanceCYAPage, TotalChargeAmountPage => AnnualAllowanceTotalChargeAmount}
import pages.chargeF.{CheckYourAnswersPage => DeRegistrationCYAPage}
import pages.chargeG.{CheckYourAnswersPage => OverseasTransferCYAPage, TotalChargeAmountPage => OverseasTransferTotalChargeAmount}
import pages.{Page, QuestionPage}

case object ChargeTypeHelper {

  private val totalChargeAmountMap = Map("annualAllowance" -> AnnualAllowanceTotalChargeAmount,
                                         "lifeTimeAllowance" -> LifetimeAllowanceTotalChargeAmount,
                                         "overseasTransfer" -> OverseasTransferTotalChargeAmount,
                                         "authSurplus" -> AuthSurplusTotalChargeAmount)

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

  def getTotalChargeAmountPage(chargeType: ChargeType): QuestionPage[BigDecimal] = {
    totalChargeAmountMap(chargeType.toString)
  }

}
