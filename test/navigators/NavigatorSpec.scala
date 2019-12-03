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
import models._
import pages.Page
import play.api.libs.json.Json
import play.api.mvc.Call

import scala.concurrent.ExecutionContext.Implicits.global

class NavigatorSpec extends SpecBase {

  private val call1 = Option(Call("GET","dest1"))
  private val call2 = Option(Call("GET","dest2"))

  private case object DummyIdentifier extends Page

//  private val dummyNavigator = new Navigator {
//    override protected def routeMap(id: Page, userAnswers: UserAnswers): Option[Call] = call1
//
//    override protected def editRouteMap(id: Page, userAnswers: UserAnswers): Option[Call] = call2
//  }
//
//  "Navigator" when {
//    "in Normal mode" must {
//      "go to correct route" in {
//        dummyNavigator.nextPageOptional(DummyIdentifier, NormalMode, UserAnswers(Json.obj())) mustBe call1
//      }
//    }
//
//    "in Check mode" must {
//      "go to correct route" in {
//        dummyNavigator.nextPageOptional(DummyIdentifier, CheckMode, UserAnswers(Json.obj())) mustBe call2
//      }
//    }
//  }
}
