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

import models.requests.DataRequest
import models.{Mode, NormalMode, UserAnswers}
import pages.QuestionPage
import play.api.libs.json._
import play.api.mvc.AnyContent

import scala.util.Try

class UserAnswersService {

  /* Use this set for add/change journeys for a member or scheme based charge */
  def set[A](page: QuestionPage[A], value: A, mode: Mode, isMemberBased: Boolean = true
            )(implicit request: DataRequest[AnyContent], writes: Writes[A]): Try[UserAnswers] = {
    val status: String = if(mode == NormalMode) "New" else "Changed"
    set(page, value, status, isMemberBased, request.userAnswers)
  }

  /* Use this set for deleting a member based charge */
  def set[A](page: QuestionPage[A], value: A, userAnswers: UserAnswers
                       )(implicit request: DataRequest[AnyContent], writes: Writes[A]): Try[UserAnswers] = {

    set(page, value, "Deleted", isMemberBased = true, userAnswers)
  }

  private def set[A](page: QuestionPage[A], value: A, status: String, isMemberBased: Boolean, userAnswers: UserAnswers
             )(implicit request: DataRequest[AnyContent], writes: Writes[A]): Try[UserAnswers] = {

        if(request.sessionData.sessionAccessData.version > 1) { //this IS an amendment
          if(isMemberBased) { //charge C, D, E or G
            val amendedVersionPath = JsPath(page.path.path.take(1) ++ List(KeyPathNode("amendedVersion")))
            val memberVersionPath = JsPath(page.path.path.init ++ List(KeyPathNode("memberAFTVersion")))
            val memberStatusPath = JsPath(page.path.path.init ++ List(KeyPathNode("memberStatus")))


            userAnswers.set(page, value)
              .flatMap(_.set(amendedVersionPath, JsNull))
              .flatMap(_.set(memberVersionPath, JsNull))
              .flatMap(_.set(memberStatusPath, JsString(status)))

          } else { //charge A, B or F
            val amendedVersionPath = JsPath(page.path.path.init ++ List(KeyPathNode("amendedVersion")))
            userAnswers.set(page, value)
              .flatMap(_.set(amendedVersionPath, JsNull))
          }
        } else { //this is NOT an amendment
          userAnswers.set(page, value)
        }

  }


  def remove[A](page: QuestionPage[A]
               )(implicit request: DataRequest[AnyContent]): Try[UserAnswers] = {
    if(request.sessionData.sessionAccessData.version > 1) { //this IS an amendment
      val amendedVersionPath = JsPath(page.path.path.init ++ List(KeyPathNode("amendedVersion")))
        request.userAnswers.remove(page) //todo - this will change to zero-out call in PODS-4201
          .flatMap(_.set(amendedVersionPath, JsNull))
    } else { //this is NOT an amendment
      request.userAnswers.remove(page)
    }
  }

}
