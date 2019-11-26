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

package navigators

import base.SpecBase
import identifiers.{Identifier, TypedIdentifier}
import models.{Mode, UserAnswers}
import org.scalatest.prop.{PropertyChecks, TableFor3}
import org.scalatest.{MustMatchers, OptionValues, WordSpec}
import play.api.libs.json.Writes
import play.api.mvc.Call
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

trait NavigatorBehaviour extends SpecBase with PropertyChecks with OptionValues {
  this: WordSpec with MustMatchers =>

  protected implicit val hc: HeaderCarrier = HeaderCarrier()

  protected def row(id: TypedIdentifier.PathDependent)(value: id.Data, call: Call, optionUA: Option[UserAnswers] = None)
                   (implicit writes: Writes[id.Data]): (id.type, UserAnswers, Call) = {
    val userAnswers = optionUA.fold(UserAnswers(userAnswersId))(identity).set(id)(value).asOpt.value
    Tuple3(id, userAnswers, call)
  }

  protected def navigatorWithRoutesForMode(mode: Mode)(navigator: Navigator,
                                                       routes: TableFor3[Identifier, UserAnswers, Call],
                                                       srn: Option[String]): Unit = {
    forAll(routes) {
      (id: Identifier, userAnswers: UserAnswers, call: Call) =>
        s"move from $id to $call in ${Mode.jsLiteral.to(mode)} with data: ${userAnswers.toString}" in {
          val result = navigator.nextPageOptional(id, mode, userAnswers, srn)
          result mustBe call
        }
    }
  }
}
