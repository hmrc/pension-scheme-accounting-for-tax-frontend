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

import models.{Mode, UserAnswers}
import org.scalatest.FreeSpec
import org.scalatest.prop.{PropertyChecks, TableFor3}
import pages.Page
import play.api.mvc.Call
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

trait NavigatorBehaviour extends FreeSpec with PropertyChecks {

  implicit val hc = HeaderCarrier
  /*protected def navigatorWithRoutesForMode(mode: Mode)(navigator: CompoundNavigator,
                                                       routes: TableFor3[Page, UserAnswers, Call],
                                                       srn: String): Unit = {
    forAll(routes) {
      (id: Page, userAnswers: UserAnswers, call: Call) =>
        s"move from $id to $call in ${Mode.jsLiteral.to(mode)} with data: ${userAnswers.toString}" in {
          val result = navigator.nextPage(id, mode, userAnswers, srn)
          result mustBe call
        }
    }
  }*/

}
