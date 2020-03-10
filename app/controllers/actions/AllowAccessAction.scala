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

package controllers.actions

import java.time.LocalDate

import com.google.inject.ImplementedBy
import com.google.inject.Inject
import models.UserAnswers
import models.requests.OptionalDataRequest
import play.api.mvc.ActionFilter
import play.api.mvc.Result
import services.AllowAccessService

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class AllowAccessAction(srn: String, startDate: LocalDate, allowService: AllowAccessService)(
    implicit val executionContext: ExecutionContext)
    extends ActionFilter[OptionalDataRequest] {
  override protected def filter[A](request: OptionalDataRequest[A]): Future[Option[Result]] =
    allowService.filterForIllegalPageAccess(srn, startDate, request.userAnswers.getOrElse(UserAnswers()))(request)
}

@ImplementedBy(classOf[AllowAccessActionProviderImpl])
trait AllowAccessActionProvider {
  def apply(srn: String, startDate: LocalDate): ActionFilter[OptionalDataRequest]
}

class AllowAccessActionProviderImpl @Inject()(allowService: AllowAccessService)(implicit ec: ExecutionContext)
    extends AllowAccessActionProvider {
  def apply(srn: String, startDate: LocalDate) = new AllowAccessAction(srn, startDate, allowService)
}
