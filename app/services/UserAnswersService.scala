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

  def set[A](page: QuestionPage[A], value: A, mode: Mode, isMemberBased: Boolean = true
             )(implicit request: DataRequest[AnyContent], writes: Writes[A]): Try[UserAnswers] = {

        if(request.sessionData.sessionAccessData.version > 1) {
          if(isMemberBased) {
            val amendedVersionPath = JsPath(page.path.path.take(1) ++ List(KeyPathNode("amendedVersion")))
            val memberVersionPath = JsPath(page.path.path.init ++ List(KeyPathNode("memberAFTVersion")))
            val memberStatusPath = JsPath(page.path.path.init ++ List(KeyPathNode("memberStatus")))
            val status = JsString(if(mode == NormalMode) "New" else "Changed")

            request.userAnswers.set(page, value)
              .flatMap(_.set(amendedVersionPath, JsNull))
              .flatMap(_.set(memberVersionPath, JsNull))
              .flatMap(_.set(memberStatusPath, status))

          } else {
            val amendedVersionPath = JsPath(page.path.path.init ++ List(KeyPathNode("amendedVersion")))
            request.userAnswers.set(page, value).flatMap(_.set(amendedVersionPath, JsNull))
          }


        } else {
          request.userAnswers.set(page, value)
        }

  }

}
