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

package navigators

import java.time.LocalDate

import base.SpecBase
import models.{Mode, UserAnswers}
import org.scalatest.MustMatchers
import org.scalatest.prop.TableFor3
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import pages.Page
import play.api.mvc.Call

trait NavigatorBehaviour extends SpecBase with MustMatchers with ScalaCheckPropertyChecks {

  protected def row(page: Page)(call: Call, ua: Option[UserAnswers] = None): (Page, UserAnswers, Call) = {
    Tuple3(page, ua.getOrElse(UserAnswers()), call)
  }

  protected def navigatorWithRoutesForMode(mode: Mode)(navigator: CompoundNavigator,
                                                       routes: TableFor3[Page, UserAnswers, Call],
                                                       srn: String, startDate: LocalDate): Unit = {
    forAll(routes) {
      (page: Page, userAnswers: UserAnswers, call: Call) =>
        s"move from $page to $call in ${Mode.jsLiteral.to(mode)} with data: ${userAnswers.toString}" in {
          val result = navigator.nextPage(page, mode, userAnswers,srn, startDate)(request())
          result mustBe call
        }
    }
  }
}
