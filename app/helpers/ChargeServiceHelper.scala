/*
 * Copyright 2021 HM Revenue & Customs
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

import models.UserAnswers
import pages.chargeC.SponsoringEmployersQuery
import pages.chargeD.LifetimeAllowanceMembersQuery
import pages.chargeE.AnnualAllowanceMembersQuery
import pages.chargeG.{ChargeAmountsPage, OverseasTransferMembersQuery}
import play.api.libs.json.{JsArray, JsValue}

class ChargeServiceHelper {

  private case class NodeInfo(
                              chargeDetailsNode:String,
                              memberOrEmployerNode: String,
                              amountNode:String
                             )

  def totalAmount(ua: UserAnswers, chargeType: String): BigDecimal = {
    val nodes : NodeInfo = nodeInfo(chargeType).get
    val deletedFilter: JsValue => Boolean = memberOrEmployer => !{(memberOrEmployer \ "memberStatus").asOpt[String].contains("Deleted")}
    (ua.data \ chargeType \ nodes.memberOrEmployerNode)
      .validate[JsArray]
      .asOpt
      .getOrElse(JsArray()).value
      .filter(deletedFilter)
      .map { nonDeletedMemberOrEmployer =>
        (nonDeletedMemberOrEmployer \ nodes.chargeDetailsNode \ nodes.amountNode).as[BigDecimal]}.seq.sum
  }

  def isEmployerOrMemberPresent(ua: UserAnswers,  chargeType: String): Boolean = {
    val nodes : NodeInfo = nodeInfo(chargeType).get
    val nonDeletedMemberOrEmployer =
      (ua.data \ chargeType \ nodes.memberOrEmployerNode)
        .validate[JsArray]
        .asOpt
        .getOrElse(JsArray()).value
        .find{ memberOrEmployer =>
          !(memberOrEmployer \ "memberStatus").validate[String].asOpt.contains("Deleted")}
    nonDeletedMemberOrEmployer.nonEmpty
  }

  private def nodeInfo(chargeType:String):Option[NodeInfo] = {
    chargeType match {
      case "chargeEDetails" =>
        Some(NodeInfo(
          chargeDetailsNode = pages.chargeE.ChargeDetailsPage.toString,
          memberOrEmployerNode = AnnualAllowanceMembersQuery.toString,
          amountNode = "chargeAmount"))
      case "chargeCDetails" =>
        Some(NodeInfo(
          chargeDetailsNode = pages.chargeC.ChargeCDetailsPage.toString,
          memberOrEmployerNode = SponsoringEmployersQuery.toString,
          amountNode = "amountTaxDue"))
      case "chargeDDetails" =>
        Some(NodeInfo(
          chargeDetailsNode = pages.chargeD.ChargeDetailsPage.toString,
          memberOrEmployerNode = LifetimeAllowanceMembersQuery.toString,
          amountNode = "total"))
      case "chargeGDetails" =>
        Some(NodeInfo(
          chargeDetailsNode = ChargeAmountsPage.toString,
          memberOrEmployerNode = OverseasTransferMembersQuery.toString,
          amountNode = "amountTaxDue"))
      case _ => None
    }
  }

}
