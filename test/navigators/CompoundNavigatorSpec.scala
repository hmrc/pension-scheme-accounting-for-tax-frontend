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
import models.{NormalMode, UserAnswers}
import pages.Page
import play.api.mvc.Call

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global

class CompoundNavigatorSpec extends SpecBase {

  private def navigator(call: Option[Call]): Navigator =
    new Navigator {
      override protected def routeMap(id: Page, userAnswers: UserAnswers): Option[Call] = call

      override protected def editRouteMap(id: Page, userAnswers: UserAnswers): Option[Call] = call
    }

  "CompoundNavigator" must {
    "delegate to the bound Navigators" in {
      val navigators = Set(
        navigator(None),
        navigator(Some(Call("GET", "www.example.com/1"))),
        navigator(None)
      )
      val compoundNavigator = new CompoundNavigatorImpl(navigators.asJava)
      object TestIdentifier extends Page
      val result = compoundNavigator.nextPageOptional(TestIdentifier, NormalMode, UserAnswers(userAnswersId), None)
      result mustEqual Option(Call("GET", "www.example.com/1"))
    }
  }
}
