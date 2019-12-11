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

package models

import models.chargeE.AnnualAllowanceMember
import pages._
import pages.chargeE.ChargeDetailsPage
import play.api.libs.json._
import play.api.mvc.Call

import scala.util.{Failure, Success, Try}

final case class UserAnswers(
                              data: JsObject = Json.obj()
                            ) {

  def get[A](page: QuestionPage[A])(implicit rds: Reads[A]): Option[A] =
    Reads.optionNoError(Reads.at(page.path)).reads(data).getOrElse(None)

    def getAllMembers: Seq[MemberDetails] =
    (data \ "members" \\ "memberDetails").map {member =>
      validate[MemberDetails](member)
    }

  def getAnnualAllowanceMembers(srn: String): Seq[AnnualAllowanceMember] = {
    println(">>>>> 3 "+getAllMembers)
    def viewUrl(index: Int): Call = controllers.chargeE.routes.MemberDetailsController.onPageLoad(NormalMode, srn, index)
    def removeUrl(index: Int): Call = controllers.chargeE.routes.MemberDetailsController.onPageLoad(NormalMode, srn, index)
    val members = for((member, index) <- getAllMembers.zipWithIndex) yield {
      get(ChargeDetailsPage(index)).map { chargeDetails =>
        AnnualAllowanceMember(
          index,
          member.fullName,
          chargeDetails.chargeAmount,
          viewUrl(index).url,
          removeUrl(index).url
        )
      }
    }
    members.flatten
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
