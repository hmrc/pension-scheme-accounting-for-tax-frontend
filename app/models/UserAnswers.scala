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

package models

import pages._
import play.api.libs.json._

import scala.util.{Failure, Success, Try}

final case class UserAnswers(
                              data: JsObject = Json.obj()
                            ) {

  def get[A](page: QuestionPage[A])(implicit rds: Reads[A]): Option[A] =
    Reads.optionNoError(Reads.at(page.path)).reads(data).getOrElse(None)

  def getAllMembersInCharge[A](charge: String)(implicit rds: Reads[A]): Seq[A] =
    (data \ charge \ "members" \\ "memberDetails").map { member =>
      validate[A](member)
    }

  private def validate[A](jsValue: JsValue)(implicit rds: Reads[A]): A = {
    jsValue.validate[A].fold(
      invalid =
        errors =>
          throw JsResultException(errors),
      valid =
        response => response
    )
  }

  def set[A](page: QuestionPage[A], value: A)(implicit writes: Writes[A]): Try[UserAnswers] = {

    val updatedData = data.setObject(page.path, Json.toJson(value)) match {
      case JsSuccess(jsValue, _) =>
        Success(jsValue)
      case JsError(errors) =>
        Failure(JsResultException(errors))
    }

    updatedData.flatMap {
      d =>
        val updatedAnswers = copy(data = d)
        page.cleanup(Some(value), updatedAnswers)
    }
  }

  def setOrException[A](page: QuestionPage[A], value: A)(implicit writes: Writes[A]): UserAnswers = {
    set(page, value) match {
      case Success(ua) => ua
      case Failure(ex) => throw ex
    }
  }

  def removeWithPath(path: JsPath): UserAnswers = {
    data.removeObject(path) match {
      case JsSuccess(jsValue, _) =>
        UserAnswers(jsValue)
      case JsError(_) =>
        throw new RuntimeException("Unable to remove with path: " + path)
    }
  }

  def remove[A](page: QuestionPage[A]): Try[UserAnswers] = {

    val updatedData = data.setObject(page.path, JsNull) match {
      case JsSuccess(jsValue, _) =>
        Success(jsValue)
      case JsError(_) =>
        Success(data)
    }

    updatedData.flatMap {
      d =>
        val updatedAnswers = copy(data = d)
        page.cleanup(None, updatedAnswers)
    }
  }
}

object UserAnswers {
  def deriveMinimumChargeValueAllowed(ua: UserAnswers): BigDecimal = {
    ua.get(IsNewReturn) match {
      case None => BigDecimal("0.00")
      case _ => BigDecimal("0.01")
    }
  }

  private val chargeEIsMemberDeleted: (UserAnswers, Int) => Boolean = (ua, index) => ua.get(pages.chargeE.MemberDetailsPage(index)).forall(_.isDeleted)

  private val chargeDIsMemberDeleted: (UserAnswers, Int) => Boolean = (ua, index) => ua.get(pages.chargeD.MemberDetailsPage(index)).forall(_.isDeleted)

  private val chargeGIsMemberDeleted: (UserAnswers, Int) => Boolean = (ua, index) => ua.get(pages.chargeG.MemberDetailsPage(index)).forall(_.isDeleted)

  private val chargeCIsEmployerDeleted: (UserAnswers, Int) => Boolean = (ua, index) =>
    (ua.get(pages.chargeC.IsSponsoringEmployerIndividualPage(index)), ua.get(pages.chargeC.SponsoringIndividualDetailsPage(index)), ua.get(pages.chargeC.SponsoringOrganisationDetailsPage(index))) match {
      case (Some(true), Some(individual), _) => individual.isDeleted
      case (Some(false), _, Some(organisation)) => organisation.isDeleted
      case _ => true
    }

  private case class ChargeInfo(jsonNode: String, memberOrEmployerJsonNode: String, isDeleted: (UserAnswers, Int) => Boolean)

  private val memberBasedChargeInfo = Seq(
    ChargeInfo(jsonNode = "chargeEDetails", memberOrEmployerJsonNode = "members",   isDeleted = chargeEIsMemberDeleted),
    ChargeInfo(jsonNode = "chargeDDetails", memberOrEmployerJsonNode = "members",   isDeleted = chargeDIsMemberDeleted),
    ChargeInfo(jsonNode = "chargeGDetails", memberOrEmployerJsonNode = "members",   isDeleted = chargeGIsMemberDeleted),
    ChargeInfo(jsonNode = "chargeCDetails", memberOrEmployerJsonNode = "employers", isDeleted = chargeCIsEmployerDeleted)
  )

  private def countNonDeletedMembersOrEmployers(ua:UserAnswers, chargeInfo: ChargeInfo):Int = {
    (ua.data \ chargeInfo.jsonNode \ chargeInfo.memberOrEmployerJsonNode).validate[JsArray] match {
      case JsSuccess(array, _) =>
        array.value.indices.map(index => chargeInfo.isDeleted(ua, index)).count(_ == false)
      case JsError(_) => 0
    }
  }

  def removeChargesHavingNoMembersOrEmployers(answers: UserAnswers): UserAnswers = {
    memberBasedChargeInfo.foldLeft(answers) { (currentUA, chargeInfo) =>
      if (countNonDeletedMembersOrEmployers(currentUA, chargeInfo) == 0) {
        currentUA.removeWithPath(JsPath \ chargeInfo.jsonNode)
      } else {
        currentUA
      }
    }
  }

}
