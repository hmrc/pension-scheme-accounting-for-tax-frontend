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
import models.requests.DataRequest
import models.{Mode, NormalMode, UserAnswers}
import pages.QuestionPage
import play.api.libs.json._
import play.api.mvc.AnyContent
import utils.DeleteChargeHelper

import scala.util.Try

class UserAnswersService @Inject()(deleteChargeHelper: DeleteChargeHelper) {

  /* Use this set for add/change journeys for a member or scheme based charge */
  def set[A](page: QuestionPage[A], value: A, mode: Mode, isMemberBased: Boolean = true
            )(implicit request: DataRequest[AnyContent], writes: Writes[A]): Try[UserAnswers] = {

    if(mainVersion > 1) { //this IS an amendment
      if(isMemberBased) { //charge C, D, E or G

        val status: String = if(mode == NormalMode) "New" else getCorrectStatus(page, "Changed", request.userAnswers)

        request.userAnswers
          .removeWithPath(amendedVersionPath(page))
          .removeWithPath(memberVersionPath(page))
          .set(page, value).flatMap(_.set(memberStatusPath(page), JsString(status)))

      } else { //charge A, B or F
        val amendedVersionPath: JsPath = JsPath(page.path.path.init ++ List(KeyPathNode("amendedVersion")))
        request.userAnswers.removeWithPath(amendedVersionPath).set(page, value)
      }
    } else { //this is NOT an amendment
      request.userAnswers.set(page, value)
    }
  }

  /* Use this set for deleting a member based charge */
  def set[A](page: QuestionPage[A], userAnswers: UserAnswers
                       )(implicit request: DataRequest[AnyContent], writes: Writes[A]): Try[UserAnswers] = {
    if(mainVersion > 1) { //this IS an amendment

            val updatedStatus: JsString = JsString(getCorrectStatus(page, "Deleted", userAnswers))

      userAnswers
        .removeWithPath(amendedVersionPath(page))
        .removeWithPath(memberVersionPath(page))
        .set(memberStatusPath(page), updatedStatus)


        } else { //this is NOT an amendment
          Try(userAnswers)
        }
  }


  def remove[A](page: QuestionPage[A]
               )(implicit request: DataRequest[AnyContent]): UserAnswers = {
    if(request.sessionData.sessionAccessData.version > 1) { //this IS an amendment
      val amendedVersionPath: JsPath = JsPath(page.path.path.init ++ List(KeyPathNode("amendedVersion")))
        deleteChargeHelper.zeroOutCharge(page, request.userAnswers).removeWithPath(amendedVersionPath)
    } else { //this is NOT an amendment
      request.userAnswers
    }
  }

  private def getCorrectStatus[A](page: QuestionPage[A], updatedStatus: String, userAnswers: UserAnswers)(implicit request: DataRequest[AnyContent]): String = {

    val previousVersion = userAnswers.get(memberVersionPath(page))
    val prevMemberStatus = userAnswers.get(memberStatusPath(page)).getOrElse(throw MissingMemberStatus)

    val isChangeInSameCompile = previousVersion.nonEmpty && previousVersion.getOrElse(throw MissingVersion).as[Int] == mainVersion

   if((previousVersion.isEmpty || isChangeInSameCompile) && prevMemberStatus.as[String].equals("New")) {
      "New"
    } else {
     updatedStatus
   }
  }

  private def mainVersion(implicit request: DataRequest[AnyContent]): Int =
    request.sessionData.sessionAccessData.version

  private def amendedVersionPath[A](page: QuestionPage[A]): JsPath =
    JsPath(page.path.path.take(1) ++ List(KeyPathNode("amendedVersion")))

  private def memberVersionPath[A](page: QuestionPage[A]): JsPath =
    JsPath(page.path.path.init ++ List(KeyPathNode("memberAFTVersion")))

  private def memberStatusPath[A](page: QuestionPage[A]): JsPath =
    JsPath(page.path.path.init ++ List(KeyPathNode("memberStatus")))

  case object MissingMemberStatus extends Exception("Previous member status was not found for an amendment")
  case object MissingVersion extends Exception("Previous version number was not found for an amendment")

}