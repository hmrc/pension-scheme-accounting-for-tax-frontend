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

package navigators

import base.SpecBase
import data.SampleData.{accessType, versionInt}
import models._
import models.requests.DataRequest
import pages.Page
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, Call}
import utils.AFTConstants.QUARTER_START_DATE

import java.time.LocalDate

class NavigatorSpec extends SpecBase {
  private val srn = "test-srn"

  private case object DummyIdentifier extends Page

  private val call1: PartialFunction[Page, Call] = {
    case DummyIdentifier => Call("GET", "/page1")
  }
  private val call2: PartialFunction[Page, Call] = {
    case DummyIdentifier => Call("GET", "/page2")
  }

  private val dummyNavigator: Navigator = new Navigator {
    override protected def routeMap(userAnswers: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
                                   (implicit request: DataRequest[AnyContent]): PartialFunction[Page, Call] = call1

    override protected def editRouteMap(userAnswers: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
                                       (implicit request: DataRequest[AnyContent]): PartialFunction[Page, Call] = call2
  }

  "Navigator" when {
    "in Normal mode" must {
      "go to correct route" in {
        dummyNavigator.nextPageOptional(NormalMode, UserAnswers(Json.obj()),srn, QUARTER_START_DATE, accessType, versionInt)(request()) mustBe call1
      }
    }

    "in Check mode" must {
      "go to correct route" in {
        dummyNavigator.nextPageOptional(CheckMode, UserAnswers(Json.obj()),srn, QUARTER_START_DATE, accessType, versionInt)(request()) mustBe call2
      }
    }
  }
}
