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

import models.AccessMode.{PageAccessModeCompile, PageAccessModePreCompile}
import models.{AccessMode, UserAnswers}
import pages.chargeC.SponsoringEmployersQuery
import pages.chargeD.LifetimeAllowanceMembersQuery
import pages.chargeE.AnnualAllowanceMembersQuery
import pages.chargeG.{ChargeAmountsPage, OverseasTransferMembersQuery}
import play.api.libs.json.{JsArray, JsValue}

class ChargeServiceHelper {

  private case class NodeInfo(
                               chargeDetailsNode: String,
                               memberOrEmployerNode: String,
                               calculateAmount: JsValue => BigDecimal
                             )

  def totalAmount(ua: UserAnswers, chargeType: String): BigDecimal = {

    val nodes: NodeInfo = nodeInfo(chargeType).get
    val deletedFilter: JsValue => Boolean = memberOrEmployer => ! {
      (memberOrEmployer \ "memberStatus").asOpt[String].contains("Deleted")
    }
    (ua.data \ chargeType \ nodes.memberOrEmployerNode)
      .validate[JsArray]
      .asOpt
      .getOrElse(JsArray()).value
      .filter(deletedFilter)
      .map { nonDeletedMemberOrEmployer =>
        nodes.calculateAmount(nonDeletedMemberOrEmployer)
      }.sum
  }


  def isEmployerOrMemberPresent(ua: UserAnswers, chargeType: String): Boolean = {
    val nodes: NodeInfo = nodeInfo(chargeType).get
    val nonDeletedMemberOrEmployer =
      (ua.data \ chargeType \ nodes.memberOrEmployerNode)
        .validate[JsArray]
        .asOpt
        .getOrElse(JsArray()).value
        .find { memberOrEmployer =>
          !(memberOrEmployer \ "memberStatus").validate[String].asOpt.contains("Deleted")
        }
    nonDeletedMemberOrEmployer.nonEmpty
  }

  def isShowFileUploadOption(ua: UserAnswers, chargeType: String, version: Int, accessMode: AccessMode): Boolean = {

    nodeInfo(chargeType) match {
      case Some(nodes) =>
        val memberSeq =
          (ua.data \ chargeType \ nodes.memberOrEmployerNode)
            .validate[JsArray].asOpt
            .getOrElse(JsArray()).value

        (accessMode, version) match {
          case (PageAccessModePreCompile, v) if v > 1 => memberSeq.isEmpty
          case (PageAccessModeCompile, v) if v > 1 => validateCompile(memberSeq.toIndexedSeq, v)
          case (_, 1) => true
          case _ => false
        }
      case _ =>
        false
    }
  }

  private def validateCompile(memberSeq: IndexedSeq[JsValue], v: Int) = {

    memberSeq.isEmpty || (!memberSeq.exists(member =>
      (member \ "memberAFTVersion").validate[Int].getOrElse(0) < v)
      || memberSeq.forall(member => (member \ "memberStatus").validate[String].asOpt.contains("New"))
      )
  }

  private def nodeInfo(chargeType: String): Option[NodeInfo] = {
    val bigDecimal_zero = BigDecimal(0.0)
    chargeType match {
      case "chargeEDetails" =>
        Some(NodeInfo(
          chargeDetailsNode = pages.chargeE.ChargeDetailsPage.toString,
          memberOrEmployerNode = AnnualAllowanceMembersQuery.toString,
          calculateAmount = rootNode => (rootNode \ pages.chargeE.ChargeDetailsPage.toString \ "chargeAmount").asOpt[BigDecimal].getOrElse(bigDecimal_zero)))
      case "chargeCDetails" =>
        Some(NodeInfo(
          chargeDetailsNode = pages.chargeC.ChargeCDetailsPage.toString,
          memberOrEmployerNode = SponsoringEmployersQuery.toString,
          calculateAmount = rootNode => (rootNode \ pages.chargeC.ChargeCDetailsPage.toString \ "amountTaxDue").asOpt[BigDecimal].getOrElse(bigDecimal_zero)))
      case "chargeDDetails" =>
        Some(NodeInfo(
          chargeDetailsNode = pages.chargeD.ChargeDetailsPage.toString,
          memberOrEmployerNode = LifetimeAllowanceMembersQuery.toString,
          calculateAmount = rootNode => {
            val amount1 = (rootNode \ pages.chargeD.ChargeDetailsPage.toString \ "taxAt25Percent").asOpt[BigDecimal].getOrElse(bigDecimal_zero)
            val amount2 = (rootNode \ pages.chargeD.ChargeDetailsPage.toString \ "taxAt55Percent").asOpt[BigDecimal].getOrElse(bigDecimal_zero)
            amount1 + amount2
          }))
      case "chargeGDetails" =>
        Some(NodeInfo(
          chargeDetailsNode = ChargeAmountsPage.toString,
          memberOrEmployerNode = OverseasTransferMembersQuery.toString,
          calculateAmount = rootNode => (rootNode \ ChargeAmountsPage.toString \ "amountTaxDue").asOpt[BigDecimal].getOrElse(bigDecimal_zero)))
      case _ => None
    }
  }

}
