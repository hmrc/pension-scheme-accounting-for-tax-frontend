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

import com.google.inject.Inject
import models.Mode
import models.UserAnswers
import pages.Page
import play.api.Logger
import play.api.mvc.Call

import scala.collection.JavaConverters._

trait CompoundNavigator {
  def nextPage(id: Page, mode: Mode, userAnswers: UserAnswers, srn: String, startDate: LocalDate): Call
}

class CompoundNavigatorImpl @Inject()(navigators: java.util.Set[Navigator]) extends CompoundNavigator {
  def nextPage(id: Page, mode: Mode, userAnswers: UserAnswers, srn: String, startDate: LocalDate): Call = {
    nextPageOptional(id, mode, userAnswers, srn, startDate)
      .getOrElse(defaultPage(id, mode))
  }

  private def defaultPage(id: Page, mode: Mode): Call = {
    Logger.warn(message = s"No navigation defined for id $id in mode $mode")
    controllers.routes.IndexController.onPageLoad()
  }

  private def nextPageOptional(id: Page, mode: Mode, userAnswers: UserAnswers, srn: String, startDate: LocalDate): Option[Call] = {
    navigators.asScala
      .find(_.nextPageOptional(mode, userAnswers, srn, startDate).isDefinedAt(id))
      .map(
        _.nextPageOptional(mode, userAnswers, srn, startDate)(id)
      )
  }
}
