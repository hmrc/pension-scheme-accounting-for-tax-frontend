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

package identifiers

import models.UserAnswers
import play.api.libs.json.{JsPath, _}

import scala.language.implicitConversions

trait Identifier {

  def path: JsPath = __ \ toString
}

object Identifier {

  implicit def toString(i: Identifier): String =
    i.toString
}

trait TypedIdentifier[A] extends TypedIdentifier.PathDependent {
  type Data = A

}

object TypedIdentifier {

  trait PathDependent extends Identifier {
    type Data

    def cleanup(value: Option[Data], userAnswers: UserAnswers): JsResult[UserAnswers] = JsSuccess(userAnswers)
  }

}
