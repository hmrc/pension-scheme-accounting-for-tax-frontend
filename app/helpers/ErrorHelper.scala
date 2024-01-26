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

package helpers

import connectors.ReturnAlreadySubmittedException
import play.api.mvc.Result
import play.api.mvc.Results._
import uk.gov.hmrc.http.HttpReads.is5xx
import uk.gov.hmrc.http.UpstreamErrorResponse

import scala.concurrent.{ExecutionContext, Future}

object ErrorHelper {

  def recoverFrom5XX(srn: String, startDate: String)(implicit ec: ExecutionContext): PartialFunction[Throwable, Future[Result]] = {
    case ReturnAlreadySubmittedException() =>
      Future.successful(Redirect(controllers.routes.CannotSubmitAFTController.onPageLoad(srn, startDate)))
    case e: UpstreamErrorResponse if(is5xx(e.statusCode)) =>
      Future(Redirect(controllers.routes.YourActionWasNotProcessedController.onPageLoad(srn, startDate)))
  }
}
