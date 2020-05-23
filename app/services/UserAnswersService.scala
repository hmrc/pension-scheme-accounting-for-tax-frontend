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

import com.google.inject.Inject
import helpers.DeleteChargeHelper
import models.requests.DataRequest
import models.Mode
import models.NormalMode
import models.UserAnswers
import pages.QuestionPage
import play.api.libs.json._
import play.api.mvc.AnyContent

import scala.util.Try

class UserAnswersService @Inject()(deleteChargeHelper: DeleteChargeHelper) {

  /* Use this set for add/change journeys for a member or scheme based charge */
  def set[A](page: QuestionPage[A], value: A, mode: Mode, isMemberBased: Boolean = true)(implicit request: DataRequest[AnyContent],
                                                                                         writes: Writes[A]): Try[UserAnswers] = {

    if (request.isAmendment) { //this IS an amendment
      if (isMemberBased) { //charge C, D, E or G

        val status: String = if (mode == NormalMode) "New" else getCorrectStatus(page, "Changed", request.userAnswers)

        request.userAnswers
          .removeWithPath(amendedVersionPath(page))
          .removeWithPath(memberVersionPath(page))
          .set(page, value)
          .flatMap(_.set(memberStatusPath(page), JsString(status)))

      } else { //charge A, B or F
        val amendedVersionPath: JsPath = JsPath(page.path.path.init ++ List(KeyPathNode("amendedVersion")))
        request.userAnswers.removeWithPath(amendedVersionPath).set(page, value)
      }
    } else { //this is NOT an amendment
      request.userAnswers.set(page, value)
    }
  }

  /* Use this set for deleting a member based charge */
  def removeMemberBasedCharge[A](page: QuestionPage[A], totalAmount: UserAnswers => BigDecimal)(implicit request: DataRequest[AnyContent],
                                                                                                writes: Writes[A]): Try[UserAnswers] = {
    // Can only physically remove if first compile or if member being removed was added in same version
    def isRemovable = {
      val ua = request.userAnswers
      def previousVersion = ua.get(memberVersionPath(page))
      def prevMemberStatus = ua.get(memberStatusPath(page)).getOrElse(throw MissingMemberStatus)
      def isChangeInSameCompile = previousVersion.nonEmpty && previousVersion.getOrElse(throw MissingVersion).as[Int] == request.aftVersion

      request.aftVersion == 1 || ((previousVersion.isEmpty || isChangeInSameCompile) && prevMemberStatus.as[String].equals("New"))
    }

    def isLastCharge = deleteChargeHelper.isLastCharge(request.userAnswers)

    def removeZeroOrUpdateAmendmentStatuses(ua:UserAnswers):UserAnswers = {
      (isLastCharge, isRemovable) match {
        case (true, _) => deleteChargeHelper.zeroOutLastCharge(ua)
        case (_, true) => removeMemberOrCharge(ua, page)
        case _         =>
          ua.removeWithPath(amendedVersionPath(page))
            .removeWithPath(memberVersionPath(page))
            .setOrException(memberStatusPath(page), JsString("Deleted"))
      }
    }

    def updateChargeTotalIfChargeExists(ua:UserAnswers):Try[UserAnswers] = {
      JsPath(page.path.path.take(1)).asSingleJsResult(ua.data).asOpt match {
        case None => Try(ua)
        case _ =>
          ua.set(totalAmountPath(page), JsNumber(totalAmount(ua)))
      }
    }

    updateChargeTotalIfChargeExists(
      removeZeroOrUpdateAmendmentStatuses(request.userAnswers)
    )
  }

  private def removeMemberOrCharge[A](ua: UserAnswers, page: QuestionPage[A]) = {
    membersPath(page)
      .asSingleJsResult(ua.data)
      .asOpt
      .map(_.as[JsArray].value.size)
      .map { totalMembersInCharge =>
        ua.removeWithPath(
          if (totalMembersInCharge == 1) { // Deleting last member in this charge
            chargePath(page)
          } else {
            memberParentPath(page)
          }
        )
      }
      .getOrElse(ua)
  }

  /* Use this remove for deleting a scheme based charge */
  def removeSchemeBasedCharge[A](page: QuestionPage[A])(implicit request: DataRequest[AnyContent]): UserAnswers = {
    val ua: UserAnswers = request.userAnswers
    if (request.isAmendment) {
      val amendedVersionPath: JsPath = page.path \ "amendedVersion"
      deleteChargeHelper.zeroOutCharge(page, ua).removeWithPath(amendedVersionPath)
    } else if (deleteChargeHelper.isLastCharge(ua)) {
      deleteChargeHelper.zeroOutCharge(page, ua)
    } else {
      ua.removeWithPath(page.path)
    }
  }

  private def getCorrectStatus[A](page: QuestionPage[A], updatedStatus: String, userAnswers: UserAnswers)(
      implicit request: DataRequest[AnyContent]): String = {

    val previousVersion = userAnswers.get(memberVersionPath(page))
    val prevMemberStatus = userAnswers.get(memberStatusPath(page)).getOrElse(throw MissingMemberStatus)

    val isChangeInSameCompile = previousVersion.nonEmpty && previousVersion.getOrElse(throw MissingVersion).as[Int] == request.aftVersion

    if ((previousVersion.isEmpty || isChangeInSameCompile) && prevMemberStatus.as[String].equals("New")) {
      "New"
    } else {
      updatedStatus
    }
  }

  private def amendedVersionPath[A](page: QuestionPage[A]): JsPath =
    JsPath(page.path.path.take(1) ++ List(KeyPathNode("amendedVersion")))

  private def totalAmountPath[A](page: QuestionPage[A]): JsPath =
    JsPath(page.path.path.take(1) ++ List(KeyPathNode("totalChargeAmount")))

  private def memberVersionPath[A](page: QuestionPage[A]): JsPath =
    JsPath(page.path.path.init ++ List(KeyPathNode("memberAFTVersion")))

  private def memberStatusPath[A](page: QuestionPage[A]): JsPath =
    JsPath(page.path.path.init ++ List(KeyPathNode("memberStatus")))

  private def memberParentPath[A](page: QuestionPage[A]): JsPath = {
    JsPath(page.path.path.take(3))
  }

  private def chargePath[A](page: QuestionPage[A]): JsPath = {
    JsPath(page.path.path.take(1))
  }

  private def membersPath[A](page: QuestionPage[A]): JsPath = {
    JsPath(page.path.path.take(2))
  }

  case object MissingMemberStatus extends Exception("Previous member status was not found for an amendment")
  case object MissingVersion extends Exception("Previous version number was not found for an amendment")

}
