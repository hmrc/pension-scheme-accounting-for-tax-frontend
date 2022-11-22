/*
 * Copyright 2022 HM Revenue & Customs
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
import models.requests.DataRequest
import models.{AccessType, NormalMode, UserAnswers}
import pages.Page
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, Call}
import utils.AFTConstants.QUARTER_START_DATE

import java.time.LocalDate
import scala.jdk.CollectionConverters.SetHasAsJava

class CompoundNavigatorSpec extends SpecBase {
  private val srn = "test-srn"

  case object PageOne extends Page

  case object PageTwo extends Page

  case object PageThree extends Page

  private def navigator(pp: PartialFunction[Page, Call]): Navigator = new Navigator {
    override protected def routeMap(userAnswers: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
                                   (implicit request: DataRequest[AnyContent]): PartialFunction[Page, Call] = pp

    override protected def editRouteMap(userAnswers: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
                                       (implicit request: DataRequest[AnyContent]): PartialFunction[Page, Call] = pp
  }

  "CompoundNavigator" must {
    "redirect to the correct page if there is navigation for the page" in {
      val navigators = Set(
        navigator({ case PageOne => Call("GET", "/page1") }),
        navigator({ case PageTwo => Call("GET", "/page2") }),
        navigator({ case PageThree => Call("GET", "/page3") })
      )
      val compoundNavigator = new CompoundNavigatorImpl(navigators.asJava)
      val result = compoundNavigator.nextPage(PageTwo, NormalMode, UserAnswers(Json.obj()), srn, QUARTER_START_DATE, accessType, versionInt)(request())
      result mustEqual Call("GET", "/page2")
    }

    "redirect to the index page if there is no navigation available for the given page" in {
      case object PageFour extends Page
      val navigators = Set(
        navigator({ case PageOne => Call("GET", "/page1") }),
        navigator({ case PageTwo => Call("GET", "/page2") }),
        navigator({ case PageThree => Call("GET", "/page3") })
      )
      val compoundNavigator = new CompoundNavigatorImpl(navigators.asJava)
      val result = compoundNavigator.nextPage(PageFour, NormalMode, UserAnswers(Json.obj()), srn, QUARTER_START_DATE, accessType, versionInt)(request())
      result mustEqual controllers.routes.IndexController.onPageLoad
    }
  }
}
