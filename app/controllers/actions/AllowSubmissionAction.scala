/*
 * Copyright 2021 HM Revenue & Customs
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

package controllers.actions

import com.google.inject.Inject
import handlers.ErrorHandler
import models.LocalDateBinder._
import models.requests.DataRequest
import pages.QuarterPage
import play.api.http.Status.NOT_FOUND
import play.api.mvc.{Result, ActionFilter}
import services.AFTService

import scala.concurrent.{Future, ExecutionContext}

class AllowSubmissionActionImpl @Inject()(aftService: AFTService, errorHandler: ErrorHandler)(implicit val executionContext: ExecutionContext)
    extends AllowSubmissionAction {

  override protected def filter[A](request: DataRequest[A]): Future[Option[Result]] =
    request.userAnswers.get(QuarterPage) match {
      case Some(quarter) if !aftService.isSubmissionDisabled(quarter.endDate) =>
        Future.successful(None)
      case _ =>
        errorHandler.onClientError(request, NOT_FOUND).map(Some.apply)
    }
}

trait AllowSubmissionAction extends ActionFilter[DataRequest]
