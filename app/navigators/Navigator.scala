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

import models._
import pages.Page
import play.api.mvc.Call
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext

trait Navigator {
  protected def routeMap(userAnswers: UserAnswers, srn: String): PartialFunction[Page, Call]

  protected def editRouteMap(userAnswers: UserAnswers, srn: String): PartialFunction[Page, Call]

  def nextPageOptional(mode: Mode, userAnswers: UserAnswers, srn: String)
                      (implicit ec: ExecutionContext, hc: HeaderCarrier): PartialFunction[Page, Call] =  {
      mode match {
        case NormalMode => routeMap(userAnswers, srn)
        case CheckMode => editRouteMap(userAnswers, srn)
      }
  }
}
