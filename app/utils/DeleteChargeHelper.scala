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
import play.api.libs.json.Reads._
import play.api.libs.json.{JsObject, Json, Reads, __, _}

class DeleteChargeHelper {

  def zeroOutLastCharge(ua: UserAnswers): UserAnswers = {

    val jsonTransformer: Reads[JsObject] = zeroOutChargeA orElse
      zeroOutChargeB orElse zeroOutChargeC orElse zeroOutChargeD orElse
      zeroOutChargeE orElse zeroOutChargeF orElse zeroOutChargeG

    if (hasLastChargeOnly(ua)) {
      ua.data.transform(jsonTransformer) match {
        case JsSuccess(value, _) => UserAnswers(value)
        case _                   => ua
      }
    } else {
      ua
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
        case Some((_, "chargeCDetails"))                                      => isLastChargeC(ua)
        case Some((_, chargeType)) if memberLevelCharges.contains(chargeType) => isLastCharge(ua, chargeType)
        case _                                                                => true
      }
    } else {
      false
    }
  }

  private def isLastCharge(ua: UserAnswers, chargeType: String): Boolean = {
    val memberDetailsPath = ua.data \ chargeType \ "members" \\ "memberDetails"
    getMembersOrEmployersCount(memberDetailsPath, isDeleted = false) == 0 &&
    getMembersOrEmployersCount(memberDetailsPath, isDeleted = true) > 0
  }

  private def isLastChargeC(ua: UserAnswers): Boolean = {
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

  private def zeroOutChargeB: Reads[JsObject] = updateJson(__ \ 'chargeBDetails \ 'chargeDetails, Json.obj(fields = "amountTaxDue" -> 0))

  private def zeroOutChargeF: Reads[JsObject] = updateJson(__ \ 'chargeFDetails \ 'chargeDetails, Json.obj(fields = "amountTaxDue" -> 0))

  private def zeroOutChargeC: Reads[JsObject] = {
    updateArray(__ \ 'chargeCDetails \ 'employers) {
      updateJson(__ \ 'chargeDetails, Json.obj(fields = "amountTaxDue" -> 0)) andThen {
        (__ \ 'whichTypeOfSponsoringEmployer).read[String].flatMap {
          case "individual" =>
            updateJson(__ \ 'sponsoringIndividualDetails, Json.obj(fields = "isDeleted" -> false))
          case "organisation" =>
            updateJson(__ \ 'sponsoringOrganisationDetails, Json.obj(fields = "isDeleted" -> false))
        }
      }
    }
  }

  private def zeroOutChargeD: Reads[JsObject] = updateArray(__ \ 'chargeDDetails \ 'members) {
    updateJson(__ \ 'chargeDetails, Json.obj(fields = "taxAt25Percent" -> 0, "taxAt55Percent" -> 0)) andThen
      updateJson(__ \ 'memberDetails, Json.obj(fields = "isDeleted" -> false))
  }

  private def zeroOutChargeE: Reads[JsObject] = updateArray(__ \ 'chargeEDetails \ 'members) {
    updateJson(__ \ 'chargeDetails, Json.obj(fields = "chargeAmount" -> 0)) andThen
      updateJson(__ \ 'memberDetails, Json.obj(fields = "isDeleted" -> false))
  }

  private def zeroOutChargeG: Reads[JsObject] = updateArray(__ \ 'chargeGDetails \ 'members) {
    updateJson(__ \ 'chargeAmounts, Json.obj("amountTransferred" -> 0, "amountTaxDue" -> 0)) andThen
      updateJson(__ \ 'memberDetails, Json.obj(fields = "isDeleted" -> false))
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
}
