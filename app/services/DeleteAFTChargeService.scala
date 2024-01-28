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

package services

import com.google.inject.Inject
import connectors.cache.UserAnswersCacheConnector
import helpers.DeleteChargeHelper
import models.UserAnswers
import models.requests.DataRequest
import play.api.mvc.AnyContent
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DeleteAFTChargeService @Inject()(
    aftService: AFTService,
    userAnswersCacheConnector: UserAnswersCacheConnector,
    deleteChargeHelper: DeleteChargeHelper
) {

  def deleteAndFileAFTReturn[A](pstr: String, answers: UserAnswers)(
    implicit ec: ExecutionContext,
    hc: HeaderCarrier,
    request: DataRequest[AnyContent]): Future[Unit] = {
    aftService.fileCompileReturn(pstr, answers).flatMap { _ =>
      if (deleteChargeHelper.allChargesDeletedOrZeroed(answers) && !request.isAmendment) {
       userAnswersCacheConnector.removeAll(request.internalId).map(_ => ())
      } else {
        userAnswersCacheConnector.save(request.internalId, answers.data).map(_ => ())
      }
    }
  }
}


