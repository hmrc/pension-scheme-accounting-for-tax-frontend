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
import models.{MemberDetails, UserAnswers}
import play.api.libs.json.Reads._
import play.api.libs.json.{JsObject, Json, Reads, __, _}

@Singleton
class AFTReturnTidyServiceCopy {

  private def chargeATransformer: Reads[JsObject] = {
    (__ \ 'chargeADetails \ 'chargeDetails).json.update(
      __.read[JsObject].map { o => o ++ Json.obj("totalAmtOfTaxDueAtHigherRate" -> 0, "totalAmtOfTaxDueAtLowerRate" -> 0, "totalAmount" -> 0)
      }
    )
  }

  private def chargeBTransformer: Reads[JsObject] = {
    (__ \ 'chargeBDetails \ 'chargeDetails).json.update(
      __.read[JsObject].map { o => o ++ Json.obj("amountTaxDue" -> 0)
      }
    )
  }

  private def chargeFTransformer: Reads[JsObject] = {
    (__ \ 'chargeFDetails \ 'chargeDetails).json.update(
      __.read[JsObject].map { o => o ++ Json.obj("amountTaxDue" -> 0)
      }
    )
  }

  private def chargeCTransformer: Reads[JsObject] = {
    (__ \ 'chargeCDetails \ 'employers).json.update(
      __.read[JsArray].map {
        case JsArray(arr) => {
          JsArray(
            Seq(
              arr.headOption match {
              case Some(firstElem) =>
                val x = (__ \ 'chargeDetails).json.update(
                  __.read[JsObject].map { o =>
                    o ++ Json.obj("amountTaxDue" -> 0)
                  }
                ) andThen {
                  (__ \ 'whichTypeOfSponsoringEmployer).read[String].flatMap {
                    case "individual" =>
                      (__ \ 'sponsoringIndividualDetails).json.update(
                        __.read[JsObject].map { o => o ++ Json.obj("isDeleted" -> false)
                        }
                      )
                    case "organisation" =>
                      (__ \ 'sponsoringOrganisationDetails).json.update(
                        __.read[JsObject].map { o => o ++ Json.obj("isDeleted" -> false)
                        }
                      )
                  }
                }
                firstElem.transform(x).asOpt.getOrElse(firstElem)
              case _ => Json.obj()
            } ) ++ arr.tail
          )
        }
      }
    )
  }

  private def chargeDTransformer: Reads[JsObject] = {
    (__ \ 'chargeDDetails \ 'members).json.update(
      __.read[JsArray].map {
        case JsArray(arr) => {
          JsArray(
            Seq(
              arr.headOption match {
                case Some(firstElem) =>
                  val x = (__ \ 'chargeDetails).json.update(
                    __.read[JsObject].map { o =>
                      o ++ Json.obj("taxAt25Percent" -> 0, "taxAt55Percent" -> 0)
                    }
                  ) andThen {
                    (__ \ 'memberDetails).json.update(
                      __.read[JsObject].map { o => o ++ Json.obj("isDeleted" -> false)
                      }
                    )
                  }
                  firstElem.transform(x).asOpt.getOrElse(firstElem)
                case _ => Json.obj()
              }) ++ arr.tail
          )
        }
      }
    )
  }

  private def chargeETransformer: Reads[JsObject] = {
    (__ \ 'chargeEDetails \ 'members).json.update(
      __.read[JsArray].map {
        case JsArray(arr) => {
          JsArray(
            Seq(
              arr.headOption match {
                case Some(firstElem) =>
                  val x = (__ \ 'chargeDetails).json.update(
                    __.read[JsObject].map { o =>
                      o ++ Json.obj("chargeAmount" -> 0)
                    }
                  ) andThen {
                    (__ \ 'memberDetails).json.update(
                      __.read[JsObject].map { o => o ++ Json.obj("isDeleted" -> false)
                      }
                    )
                  }
                  firstElem.transform(x).asOpt.getOrElse(firstElem)
                case _ => Json.obj()
              }) ++ arr.tail
          )
        }
      }
    )
  }

  private def chargeGTransformer: Reads[JsObject] = {
    (__ \ 'chargeGDetails \ 'members).json.update(
      __.read[JsArray].map {
        case JsArray(arr) => {
          JsArray(
            Seq(
              arr.headOption match {
                case Some(firstElem) =>
                  val x = (__ \ 'chargeAmounts).json.update(
                    __.read[JsObject].map { o =>
                      o ++ Json.obj("amountTransferred" -> 0, "amountTaxDue" -> 0)
                    }
                  ) andThen {
                    (__ \ 'memberDetails).json.update(
                      __.read[JsObject].map { o => o ++ Json.obj("isDeleted" -> false)
                      }
                    )
                  }
                  firstElem.transform(x).asOpt.getOrElse(firstElem)
                case _ => Json.obj()
              }) ++ arr.tail
          )
        }
      }
    )
  }


  def hasLastChargeOnly(ua: UserAnswers): Boolean = {

    val json = ua.data
    Seq(
      (json \ "chargeADetails").isDefined,
      (json \ "chargeBDetails").isDefined,
      (json \ "chargeCDetails").isDefined,
      ((json \ "chargeDDetails").isDefined &&
        ua.getAllMembersInCharge[MemberDetails]("chargeDDetails").count(_.isDeleted == false) == 0 &&
        ua.getAllMembersInCharge[MemberDetails]("chargeDDetails").count(_.isDeleted == true) > 0),
      ((json \ "chargeEDetails").isDefined &&
        ua.getAllMembersInCharge[MemberDetails]("chargeEDetails").count(_.isDeleted == false) == 0 &&
        ua.getAllMembersInCharge[MemberDetails]("chargeEDetails").count(_.isDeleted == true) > 0),
      (json \ "chargeFDetails").isDefined,
      ((json \ "chargeGDetails").isDefined &&
        ua.getAllMembersInCharge[models.chargeG.MemberDetails]("chargeGDetails").count(_.isDeleted == false) == 0 &&
        ua.getAllMembersInCharge[models.chargeG.MemberDetails]("chargeGDetails").count(_.isDeleted == true) > 0)
    ).count(_ == true).equals(1)
  }

  def zeroOutLastCharge(ua: UserAnswers): UserAnswers = {
    lazy val jsonTransformer: Reads[JsObject] = chargeATransformer orElse
      chargeBTransformer orElse chargeCTransformer orElse chargeDTransformer orElse
      chargeETransformer orElse chargeFTransformer orElse chargeGTransformer

    if(hasLastChargeOnly(ua)) {
      ua.data.transform(jsonTransformer) match {
        case JsSuccess(value, _) => {
          UserAnswers(value)
        }
        case _ => ua
      }
    } else {
      ua
    }
  }
}
