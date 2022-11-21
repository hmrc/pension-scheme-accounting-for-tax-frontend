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

import com.google.inject.Inject
import models.requests.DataRequest
import models.{AccessType, Mode, UserAnswers}
import pages.Page
import play.api.Logger
import play.api.mvc.{AnyContent, Call}

import java.time.LocalDate
import scala.jdk.CollectionConverters.CollectionHasAsScala

trait CompoundNavigator {
  def nextPage(
                id: Page,
                mode: Mode,
                userAnswers: UserAnswers,
                srn: String,
                startDate: LocalDate,
                accessType: AccessType,
                version: Int
              )(implicit request: DataRequest[AnyContent]): Call
}

class CompoundNavigatorImpl @Inject()(navigators: java.util.Set[Navigator])
  extends CompoundNavigator {

  private val logger = Logger(classOf[CompoundNavigatorImpl])

  private def defaultPage(id: Page, mode: Mode): Call = {
    logger.warn(s"No navigation defined for id $id in mode $mode")
    controllers.routes.IndexController.onPageLoad
  }

  def nextPage(
                id: Page,
                mode: Mode,
                userAnswers: UserAnswers,
                srn: String,
                startDate: LocalDate,
                accessType: AccessType,
                version: Int
              )(implicit request: DataRequest[AnyContent]): Call =
    nextPageOptional(id, mode, userAnswers, srn, startDate, accessType, version)
      .getOrElse(defaultPage(id, mode))

  private def nextPageOptional(
                                id: Page,
                                mode: Mode,
                                userAnswers: UserAnswers,
                                srn: String,
                                startDate: LocalDate,
                                accessType: AccessType,
                                version: Int
                              )(implicit request: DataRequest[AnyContent]): Option[Call] =
    navigators.asScala
      .find(_.nextPageOptional(mode, userAnswers, srn, startDate, accessType, version).isDefinedAt(id))
      .map(_.nextPageOptional(mode, userAnswers, srn, startDate, accessType, version)(implicitly)(id))
}
