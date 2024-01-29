/*
 * Copyright 2024 HM Revenue & Customs
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
import models.requests.DataRequest
import models.{AccessMode, AccessType, Mode, SessionAccessData, UserAnswers}
import org.scalatest.matchers.must.Matchers
import org.scalatest.prop.{TableFor3, TableFor5}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import pages.Page
import play.api.mvc.{AnyContent, Call}
import utils.DateHelper

import java.time.LocalDate

trait NavigatorBehaviour extends SpecBase with Matchers with ScalaCheckPropertyChecks {

  protected def row(page: Page)(call: Call, ua: Option[UserAnswers] = None): (Page, UserAnswers, Call) = {
    Tuple3(page, ua.getOrElse(UserAnswers()), call)
  }

  protected def rowWithDateAndVersion(page: Page)(call: Call, ua: Option[UserAnswers] = None,
    currentDate: LocalDate, version: Int): (Page, UserAnswers, Call, LocalDate, Int) = {
    Tuple5(page, ua.getOrElse(UserAnswers()), call, currentDate, version)
  }

  protected def rowWithAccessModeAndVersion(page: Page)(call: Call, ua: Option[UserAnswers] = None,
                                                        accessMode: AccessMode, version: Int): (Page, UserAnswers, Call, AccessMode, Int) = {
    Tuple5(page, ua.getOrElse(UserAnswers()), call, accessMode, version)
  }

  protected def navigatorWithRoutesForMode(mode: Mode)(navigator: CompoundNavigator,
                                                       routes: TableFor3[Page, UserAnswers, Call],
                                                       srn: String,
                                                       startDate: LocalDate,
                                                       accessType: AccessType,
                                                       version: Int,
                                                       dataRequest: DataRequest[AnyContent] = request()
                                                      ): Unit = {
    forAll(routes) {
      (page: Page, userAnswers: UserAnswers, call: Call) =>
        s"move from $page to $call in ${Mode.jsLiteral.to(mode)} with data: " +
          s"${userAnswers.toString} for psaId ${dataRequest.psaId} and pspId ${dataRequest.pspId}" in {
          DateHelper.setDate(Option(LocalDate.now))
          val result = navigator.nextPage(page, mode, userAnswers,srn, startDate, accessType, version)(dataRequest)
          result mustBe call
        }
    }
  }

  protected def navigatorWithRoutesForModeDateAndVersion(mode: Mode)(navigator: CompoundNavigator,
                                                                     routes: TableFor5[Page, UserAnswers, Call, LocalDate, Int],
                                                                     srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Unit = {
    forAll(routes) {
      (page: Page, userAnswers: UserAnswers, call: Call, currentDate: LocalDate, version: Int) =>
        s"move from $page to $call in ${Mode.jsLiteral.to(mode)} with data: ${userAnswers.toString} and current date: $currentDate and version: $version" in {
          DateHelper.setDate(Option(currentDate))
          val result = navigator.nextPage(page, mode, userAnswers, srn, startDate, accessType, version)(
            request(sessionAccessData=SessionAccessData(version = version,
              accessMode = AccessMode.PageAccessModeCompile, areSubmittedVersionsAvailable = false)))
          result mustBe call
        }
    }
  }
  protected def navigatorWithRoutesForModeAccessModeAndVersion(mode: Mode)(navigator: CompoundNavigator,
                                                                     routes: TableFor5[Page, UserAnswers, Call, AccessMode, Int],
                                                                     srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Unit = {
    forAll(routes) {
      (page: Page, userAnswers: UserAnswers, call: Call, accessMode: AccessMode, version: Int) =>
        s"move from $page to $call in ${Mode.jsLiteral.to(mode)} with data: ${userAnswers.toString} and AccessMode: $accessMode and version: $version" in {
          DateHelper.setDate(Option(LocalDate.now))
          val result = navigator.nextPage(page, mode, userAnswers, srn, startDate, accessType, version)(
            request(sessionAccessData=SessionAccessData(version = version,
              accessMode = accessMode, areSubmittedVersionsAvailable = false)))
          result mustBe call
        }
    }
  }

}
