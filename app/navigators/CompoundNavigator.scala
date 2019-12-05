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

import com.google.inject.Inject
import models.{Mode, UserAnswers}
import pages.Page
import play.api.Logger
import play.api.mvc.Call

import scala.collection.JavaConverters._

trait CompoundNavigator {
  def nextPage(id: Page, mode: Mode, userAnswers: UserAnswers, srn: String): Call
}


class CompoundNavigatorImpl @Inject()(navigators: java.util.Set[Navigator]) extends CompoundNavigator {
  private def defaultPage(id: Page, mode: Mode): Call = {
    Logger.warn(message = s"No navigation defined for id $id in mode $mode")
    controllers.routes.IndexController.onPageLoad()
  }

  def nextPage(id: Page, mode: Mode, userAnswers: UserAnswers, srn: String): Call = {
    nextPageOptional(id, mode, userAnswers, srn)
      .getOrElse(defaultPage(id, mode))
  }

  private def nextPageOptional(id: Page, mode: Mode, userAnswers: UserAnswers, srn: String): Option[Call] = {
    navigators.asScala.find(_.nextPageOptional(mode, userAnswers, srn).isDefinedAt(id)).map(
      _.nextPageOptional(mode, userAnswers, srn)(id)
    )
  }
}
