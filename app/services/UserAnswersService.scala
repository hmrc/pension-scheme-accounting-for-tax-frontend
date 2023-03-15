/*
 * Copyright 2023 HM Revenue & Customs
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
import models.{AmendedChargeStatus, Mode, NormalMode, UserAnswers}
import pages.QuestionPage
import play.api.libs.json._
import play.api.mvc.AnyContent

import scala.util.Try

class UserAnswersService @Inject()(deleteChargeHelper: DeleteChargeHelper,
                                   chargeCService: ChargeCService) {

  /* Use this set for add/change journeys for a member or scheme based charge */
  def set[A](page: QuestionPage[A], value: A, mode: Mode, isMemberBased: Boolean = true)(implicit request: DataRequest[AnyContent],
                                                                                         writes: Writes[A]): Try[UserAnswers] = {

    if (request.isAmendment) { //this IS an amendment
      if (isMemberBased) { //charge C, D, E or G

        val status: String = if (mode == NormalMode) {
          AmendedChargeStatus.Added.toString
        } else {
          getCorrectStatus(page, AmendedChargeStatus.Updated.toString, request.userAnswers)
        }

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

  /* Use this for deleting a member-based charge */
  def removeMemberBasedCharge[A](page: QuestionPage[A], totalAmount: UserAnswers => BigDecimal)(implicit
    request: DataRequest[AnyContent]): Try[UserAnswers] =
    updateChargeTotalIfChargeExists(
      removeZeroOrUpdateAmendmentStatuses(request.userAnswers, page, request.aftVersion),
      page,
      totalAmount
    )

  private def updateChargeTotalIfChargeExists[A](ua: UserAnswers, page: QuestionPage[A], totalAmount: UserAnswers => BigDecimal): Try[UserAnswers] =
    chargePath(page).asSingleJsResult(ua.data).asOpt match {
      case None => Try(ua)
      case _ =>
        ua.set(totalAmountPath(page), JsNumber(totalAmount(ua)))
    }

  private def removeZeroOrUpdateAmendmentStatuses[A](ua: UserAnswers, page: QuestionPage[A], version: Int): UserAnswers = {
    // Either physically remove, zero or update the amendment status flags for the member-based charge

    if (deleteChargeHelper.isLastCharge(ua)) { // Last charge/ member on last charge
      deleteChargeHelper.zeroOutLastCharge(ua)
    } else if (version == 1 || isAddedInAmendmentOfSameVersion(ua, page, version)) {
      removeMemberOrCharge(ua, page)
    } else { // Amendments and not added in same version
      ua.removeWithPath(amendedVersionPath(page))
        .removeWithPath(memberVersionPath(page))
        .setOrException(memberStatusPath(page), JsString(AmendedChargeStatus.Deleted.toString))
    }
  }

  private def removeMemberOrCharge[A](ua: UserAnswers, page: QuestionPage[A]) = {
    // Either remove the member from charge, or if it is the only member in the charge then remove the whole charge
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

  /* Use this remove for deleting a scheme-based charge */
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
      implicit request: DataRequest[AnyContent]): String =
    if (isAddedInAmendmentOfSameVersion(userAnswers, page, request.aftVersion)) {
      AmendedChargeStatus.Added.toString
    } else {
      updatedStatus
    }

  private def isAddedInAmendmentOfSameVersion[A](ua: UserAnswers, page: QuestionPage[A], version:Int) = {
    val previousVersion = ua.get(memberVersionPath(page))

    def isChangeInSameCompile = previousVersion.nonEmpty && previousVersion.getOrElse(throw MissingVersion).as[Int] == version
    val prevMemberStatus = ua.get(memberStatusPath(page)).getOrElse(throw MissingMemberStatus).as[String]
    (previousVersion.isEmpty || isChangeInSameCompile) && prevMemberStatus == AmendedChargeStatus.Added.toString
  }

  private def amendedVersionPath[A](page: QuestionPage[A]): JsPath =
    JsPath(page.path.path.take(1) ++ List(KeyPathNode("amendedVersion")))

  private def totalAmountPath[A](page: QuestionPage[A]): JsPath =
    JsPath(page.path.path.take(1) ++ List(KeyPathNode("totalChargeAmount")))

  private val isPathNodeMcCloud: PathNode => Boolean = _.toJsonString.endsWith("mccloudRemedy")

  private def isMcCloudField(p: List[PathNode]) = p.exists(isPathNodeMcCloud)

  private def pathNodesAboveMcCloud(p: List[PathNode]): List[PathNode] = p.init.takeWhile(n => !isPathNodeMcCloud(n))

  private def memberVersionPath[A](page: QuestionPage[A]): JsPath = {
    val p = page.path.path
    if (isMcCloudField(p)) {
      JsPath(pathNodesAboveMcCloud(p) ++ List(KeyPathNode("memberAFTVersion")))
    } else {
      JsPath(p.init ++ List(KeyPathNode("memberAFTVersion")))
    }
  }

  private def memberStatusPath[A](page: QuestionPage[A]): JsPath = {
    val p = page.path.path
    if (isMcCloudField(p)) {
      JsPath(pathNodesAboveMcCloud(p) ++ List(KeyPathNode("memberStatus")))
    } else {
      JsPath(p.init ++ List(KeyPathNode("memberStatus")))
    }
  }

  private def memberParentPath[A](page: QuestionPage[A]): JsPath = {
    JsPath(page.path.path.take(3))
  }

  private def chargePath[A](page: QuestionPage[A]): JsPath =
    JsPath(page.path.path.take(1))

  private def membersPath[A](page: QuestionPage[A]): JsPath =
    JsPath(page.path.path.take(2))

  case object MissingMemberStatus extends Exception("Previous member status was not found for an amendment")
  case object MissingVersion extends Exception("Previous version number was not found for an amendment")
}
