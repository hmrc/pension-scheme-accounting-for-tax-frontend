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

package utils

import models.UserAnswers
import pages.QuestionPage
import pages.chargeA.ShortServiceRefundQuery
import pages.chargeB.SpecialDeathBenefitsQuery
import pages.chargeF.DeregistrationQuery
import play.api.libs.json.Reads._
import play.api.libs.json.{JsObject, Json, Reads, __, _}

class DeleteChargeHelper {

  def zeroOutLastCharge(ua: UserAnswers): UserAnswers = {

    val zeroOutTransformer: Reads[JsObject] = zeroOutChargeA orElse
      zeroOutChargeB orElse zeroOutChargeC orElse zeroOutChargeD orElse
      zeroOutChargeE orElse zeroOutChargeF orElse zeroOutChargeG

    if (isLastCharge(ua)) {
      ua.data.transform(zeroOutTransformer) match {
        case JsSuccess(value, _) => UserAnswers(value)
        case _                   => ua
      }
    } else {
      ua
    }
  }

  def zeroOutCharge[A](page: QuestionPage[A], ua: UserAnswers): UserAnswers = {

    val zeroOutTransformer: Reads[JsObject] = page match {
      case ShortServiceRefundQuery => zeroOutChargeA
      case SpecialDeathBenefitsQuery => zeroOutChargeB
      case DeregistrationQuery => zeroOutChargeF
      case _ => __.json.put(Json.obj())
    }

    ua.data.transform(zeroOutTransformer) match {
      case JsSuccess(value, _) => UserAnswers(value)
      case _                   => ua
    }
  }

  def hasLastChargeOnly(ua: UserAnswers): Boolean = {
    val json = ua.data

    val memberLevelCharges = Seq("chargeCDetails", "chargeDDetails", "chargeEDetails", "chargeGDetails")
    val schemeLevelCharges = Seq("chargeADetails", "chargeBDetails", "chargeFDetails")

    val allNonEmptyCharges: Seq[(Boolean, String)] =
      (memberLevelCharges.map(chargeDetails => ((json \ chargeDetails).isDefined, chargeDetails)) ++
        schemeLevelCharges.map(chargeDetails => ((json \ chargeDetails).isDefined, chargeDetails)))
        .filter(_._1)

    if (allNonEmptyCharges.size == 1) {
      allNonEmptyCharges.headOption match {
        case Some((_, "chargeCDetails"))                                      => isLastMemberC(ua)
        case Some((_, chargeType)) if memberLevelCharges.contains(chargeType) => isLastMember(ua, chargeType)
        case _                                                                => true
      }
    } else {
      false
    }
  }

  private def isLastMember(ua: UserAnswers, chargeType: String): Boolean = {
    val memberDetailsPath = ua.data \ chargeType \ "members" \\ "memberDetails"
    getMembersOrEmployersCount(memberDetailsPath, isDeleted = false) == 0 &&
      getMembersOrEmployersCount(memberDetailsPath, isDeleted = true) > 0
  }

  private def isLastMemberC(ua: UserAnswers): Boolean = {
    val individualPath = ua.data \ "chargeCDetails" \ "employers" \\ "sponsoringIndividualDetails"
    val orgPath = ua.data \ "chargeCDetails" \ "employers" \\ "sponsoringOrganisationDetails"

    getMembersOrEmployersCount(individualPath, isDeleted = false) == 0 &&
      getMembersOrEmployersCount(orgPath, isDeleted = false) == 0 &&
      (getMembersOrEmployersCount(individualPath, isDeleted = true) > 0 ||
        getMembersOrEmployersCount(orgPath, isDeleted = true) > 0)
  }

  private def zeroOutChargeA: Reads[JsObject] =
    updateJson(__ \ 'chargeADetails \ 'chargeDetails,
      Json.obj(fields = "totalAmtOfTaxDueAtHigherRate" -> 0, "totalAmtOfTaxDueAtLowerRate" -> 0, "totalAmount" -> 0))

  private def zeroOutChargeB: Reads[JsObject] =
    updateJson(__ \ 'chargeBDetails \ 'chargeDetails, Json.obj(fields = "totalAmount" -> 0))

  private def zeroOutChargeF: Reads[JsObject] =
    updateJson(__ \ 'chargeFDetails \ 'chargeDetails, Json.obj(fields = "totalAmount" -> 0))

  private def zeroOutChargeC: Reads[JsObject] =
    updateArray(__ \ 'chargeCDetails \ 'employers) {
      updateJson(__ \ 'chargeDetails, Json.obj(fields = "amountTaxDue" -> 0))
    }

  private def zeroOutChargeD: Reads[JsObject] =
    updateArray(__ \ 'chargeDDetails \ 'members) {
      updateJson(__ \ 'chargeDetails, Json.obj(fields = "taxAt25Percent" -> 0, "taxAt55Percent" -> 0))
    }

  private def zeroOutChargeE: Reads[JsObject] =
    updateArray(__ \ 'chargeEDetails \ 'members) {
      updateJson(__ \ 'chargeDetails, Json.obj(fields = "chargeAmount" -> 0))
    }

  private def zeroOutChargeG: Reads[JsObject] = updateArray(__ \ 'chargeGDetails \ 'members) {
    updateJson(__ \ 'chargeAmounts, Json.obj("amountTransferred" -> 0, "amountTaxDue" -> 0))
  }

  private def updateJson(path: JsPath, updateJson: JsObject): Reads[JsObject] = {
    path.json.update(
      __.read[JsObject].map(o => o ++ updateJson)
    )
  }

  private def updateArray(path: JsPath)(memberTransformer: Reads[JsObject]): Reads[JsObject] = {
    path.json.update(
      __.read[JsArray].map {
        case JsArray(arr) =>
          JsArray(
            Seq(arr.headOption match {
              case Some(firstElem) =>
                firstElem.transform(memberTransformer).asOpt.getOrElse(firstElem)
              case _ => Json.obj()
            }) ++ arr.tail
          )
      }
    )
  }

  private def getMembersOrEmployersCount(seqMember: Seq[JsValue], isDeleted: Boolean): Int = {
    seqMember.count { member =>
      (member \ "isDeleted").validate[Boolean] match {
        case JsSuccess(value, _) => value == isDeleted
        case JsError(errors)     => throw JsResultException(errors)
      }
    }
  }

  private def getTotal(path: JsLookupResult): BigDecimal = {
    if(path.isDefined) {
      path.validate[BigDecimal] match {
        case JsSuccess(value, _) => value
        case JsError(errors) => throw JsResultException(errors)
      }
    }
    else {
      BigDecimal(0.00)
    }
  }

  def isLastCharge(ua: UserAnswers): Boolean = (nonZeroSchemeBasedCharges(ua) + validMemberBasedCharges(ua).count) == 1
  def allChargesDeletedOrZeroed(ua: UserAnswers): Boolean =
    nonZeroSchemeBasedCharges(ua) == 0 && validMemberBasedCharges(ua).count <= 1 && validMemberBasedCharges(ua).total == BigDecimal(0.00)

  private def nonZeroSchemeBasedCharges(ua: UserAnswers): Int = {
    val json = ua.data
    def nonZero(chargeType: String) = (json \ chargeType).isDefined && (json \ chargeType \ "chargeDetails"\ "totalAmount").as[BigDecimal] > BigDecimal(0.00)
      Seq("chargeADetails", "chargeBDetails", "chargeFDetails").count(nonZero)
  }

  def validMemberBasedCharges(ua: UserAnswers): ValidChargeDetails = {
    val memberLevelCharges = Seq("chargeCDetails", "chargeDDetails", "chargeEDetails", "chargeGDetails")

    val members = memberLevelCharges.map {
      case "chargeCDetails" => validEmployers(ua)
      case chargeType => validMembers(ua, chargeType)
    }

    val count: Int = members.map(_.count).sum
    val totalAmount: BigDecimal = members.map(_.total).sum
    ValidChargeDetails("allMembers", count, totalAmount)
  }

  private def validMembers(ua: UserAnswers, chargeType: String): ValidChargeDetails = {
    val memberDetailsPath = ua.data \ chargeType \ "members" \\ "memberDetails"
    val totalAmountPath = ua.data \ chargeType \ "totalChargeAmount"
    ValidChargeDetails(chargeType, getMembersOrEmployersCount(memberDetailsPath, isDeleted = false), getTotal(totalAmountPath))
  }

  private def validEmployers(ua: UserAnswers): ValidChargeDetails = {
    val individualPath = ua.data \ "chargeCDetails" \ "employers" \\ "sponsoringIndividualDetails"
    val orgPath = ua.data \ "chargeCDetails" \ "employers" \\ "sponsoringOrganisationDetails"
    val totalAmountPath = ua.data \ "chargeCDetails" \ "totalChargeAmount"
    val count = getMembersOrEmployersCount(individualPath, isDeleted = false) + getMembersOrEmployersCount(orgPath, isDeleted = false)
    ValidChargeDetails("chargeCDetails", count, getTotal(totalAmountPath))
  }

  case class ValidChargeDetails(chargeType: String, count: Int, total: BigDecimal)
}
