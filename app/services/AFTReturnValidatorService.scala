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

package services

import javax.inject.Singleton
import models.UserAnswers
import play.api.libs.json.{JsArray, JsError, JsPath, JsSuccess}

@Singleton
class AFTReturnValidatorService {
  private val zeroCurrencyValue = BigDecimal(0.00)

  private case class ChargeInfo(jsonNode: String,
                        memberOrEmployerJsonNode: String,
                        isDeleted: (UserAnswers, Int) => Boolean,
                        reinstate: (UserAnswers, Int) => UserAnswers
                       )

  private val chargeInfoC = ChargeInfo(
    jsonNode = "chargeCDetails",
    memberOrEmployerJsonNode = "employers",
    isDeleted = (ua, index) =>
      (ua.get(pages.chargeC.IsSponsoringEmployerIndividualPage(index)), ua.get(pages.chargeC.SponsoringIndividualDetailsPage(index)), ua.get(pages.chargeC.SponsoringOrganisationDetailsPage(index))) match {
        case (Some(true), Some(individual), _) => individual.isDeleted
        case (Some(false), _, Some(organisation)) => organisation.isDeleted
        case _ => true
      },
    reinstate = (ua, index) => {
      val uaWithEmployerReinstated = if (ua.getOrException(pages.chargeC.IsSponsoringEmployerIndividualPage(index))) {
        ua.setOrException(pages.chargeC.SponsoringIndividualDetailsPage(index),
          ua.getOrException(pages.chargeC.SponsoringIndividualDetailsPage(index)) copy (isDeleted = false)
        )
      } else {
        ua.setOrException(pages.chargeC.SponsoringOrganisationDetailsPage(index),
          ua.getOrException(pages.chargeC.SponsoringOrganisationDetailsPage(index)) copy (isDeleted = false)
        )
      }
      uaWithEmployerReinstated
        .setOrException(pages.chargeC.ChargeCDetailsPage(index),
          uaWithEmployerReinstated.getOrException(pages.chargeC.ChargeCDetailsPage(index)) copy (amountTaxDue = zeroCurrencyValue)
        )
    }
  )

  private val chargeInfoD = ChargeInfo(
    jsonNode = "chargeDDetails",
    memberOrEmployerJsonNode = "members",
    isDeleted = (ua, index) => ua.get(pages.chargeD.MemberDetailsPage(index)).forall(_.isDeleted),
    reinstate = (ua, index) => {
      val memberDetails = ua.getOrException(pages.chargeD.MemberDetailsPage(index)) copy (isDeleted = false)
      val chargeDetails = ua.getOrException(pages.chargeD.ChargeDetailsPage(index)) copy(
        taxAt25Percent = Option(zeroCurrencyValue),
        taxAt55Percent = Option(zeroCurrencyValue)
      )
      ua
        .setOrException(pages.chargeD.MemberDetailsPage(index), memberDetails)
        .setOrException(pages.chargeD.ChargeDetailsPage(index), chargeDetails)
    }
  )

  private val chargeInfoE = ChargeInfo(
    jsonNode = "chargeEDetails",
    memberOrEmployerJsonNode = "members",
    isDeleted = (ua, index) => ua.get(pages.chargeE.MemberDetailsPage(index)).forall(_.isDeleted),
    reinstate = (ua, index) => {
      val memberDetails = ua.getOrException(pages.chargeE.MemberDetailsPage(index)) copy (isDeleted = false)
      val chargeDetails = ua.getOrException(pages.chargeE.ChargeDetailsPage(index)) copy (chargeAmount = zeroCurrencyValue)
      ua
        .setOrException(pages.chargeE.MemberDetailsPage(index), memberDetails)
        .setOrException(pages.chargeE.ChargeDetailsPage(index), chargeDetails)
    }
  )

  private val chargeInfoG = ChargeInfo(
    jsonNode = "chargeGDetails",
    memberOrEmployerJsonNode = "members",
    isDeleted = (ua, index) => ua.get(pages.chargeG.MemberDetailsPage(index)).forall(_.isDeleted),
    reinstate = (ua, index) => {
      val memberDetails = ua.getOrException(pages.chargeG.MemberDetailsPage(index)) copy (isDeleted = false)
      val chargeAmounts = ua.getOrException(pages.chargeG.ChargeAmountsPage(index)) copy(
        amountTransferred = zeroCurrencyValue,
        amountTaxDue = zeroCurrencyValue
      )
      ua
        .setOrException(pages.chargeG.MemberDetailsPage(index), memberDetails)
        .setOrException(pages.chargeG.ChargeAmountsPage(index), chargeAmounts)
    }
  )

  def isAtLeastOneValidCharge(ua: UserAnswers): Boolean =
    ua.get(pages.chargeA.ChargeDetailsPage).isDefined ||
      ua.get(pages.chargeB.ChargeBDetailsPage).isDefined ||
      countMembersOrEmployers(ua, chargeInfoC) > 0 ||
      countMembersOrEmployers(ua, chargeInfoD) > 0 ||
      countMembersOrEmployers(ua, chargeInfoE) > 0 ||
      ua.get(pages.chargeF.ChargeDetailsPage).isDefined ||
      countMembersOrEmployers(ua, chargeInfoG) > 0

  def removeChargesHavingNoMembersOrEmployers(answers: UserAnswers): UserAnswers =
    Seq(chargeInfoC, chargeInfoD, chargeInfoE, chargeInfoG).foldLeft(answers) { (currentUA, chargeInfo) =>
      if (countMembersOrEmployers(currentUA, chargeInfo) == 0) {
        currentUA.removeWithPath(JsPath \ chargeInfo.jsonNode)
      } else {
        currentUA
      }
    }

  def reinstateDeletedMemberOrEmployerCharge(answers: UserAnswers): UserAnswers =
    reinstateDeletedMemberOrEmployerCharge(answers, getDeletedMemberOrEmployerChargeInfoToReinstate(answers))

  private def getDeletedMemberOrEmployerChargeInfoToReinstate(ua: UserAnswers): Option[ChargeInfo] =
    Seq(
      if (countMembersOrEmployers(ua, chargeInfoC) == 0 && countMembersOrEmployers(ua, chargeInfoC, isDeleted = true) > 0) Seq(chargeInfoC) else Seq.empty,
      if (countMembersOrEmployers(ua, chargeInfoD) == 0 && countMembersOrEmployers(ua, chargeInfoD, isDeleted = true) > 0) Seq(chargeInfoD) else Seq.empty,
      if (countMembersOrEmployers(ua, chargeInfoE) == 0 && countMembersOrEmployers(ua, chargeInfoE, isDeleted = true) > 0) Seq(chargeInfoE) else Seq.empty,
      if (countMembersOrEmployers(ua, chargeInfoG) == 0 && countMembersOrEmployers(ua, chargeInfoG, isDeleted = true) > 0) Seq(chargeInfoG) else Seq.empty
    ).flatten.headOption

  private def countMembersOrEmployers(ua: UserAnswers, chargeInfo: ChargeInfo, isDeleted: Boolean = false): Int =
    (ua.data \ chargeInfo.jsonNode \ chargeInfo.memberOrEmployerJsonNode).validate[JsArray] match {
      case JsSuccess(array, _) =>
        array.value.indices.map(index => chargeInfo.isDeleted(ua, index)).count(_ == isDeleted)
      case JsError(_) => 0
    }

  private def reinstateDeletedMemberOrEmployerCharge(ua: UserAnswers, optionWhichCharge: Option[ChargeInfo]): UserAnswers =
    optionWhichCharge.map { whichCharge =>
      val updatedUA = (ua.data \ whichCharge.jsonNode \ whichCharge.memberOrEmployerJsonNode).validate[JsArray] match {
        case JsSuccess(array, _) if array.value.nonEmpty =>
          val itemToReinstate = array.value.size - 1
          whichCharge.reinstate(ua, itemToReinstate)
        case JsError(_) => throw new RuntimeException("No members/ employers found when trying to reinstate deleted item for " + whichCharge)
      }
      updatedUA
    }.getOrElse(ua)
}

