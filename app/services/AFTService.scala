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

package services

import com.google.inject.Inject
import connectors.AFTConnector
import connectors.cache.UserAnswersCacheConnector
import models.UserAnswers
import models.requests.DataRequest
import pages.IsNewReturn
import play.api.libs.json.JsValue
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class AFTService @Inject()(
                          aftConnector: AFTConnector,
                          userAnswersCacheConnector: UserAnswersCacheConnector
                          ) {
  def fileAFTReturn(pstr: String, answers: UserAnswers)(implicit ec: ExecutionContext, hc: HeaderCarrier, request:DataRequest[_]): Future[Unit] = {
    aftConnector.fileAFTReturn(pstr, answers).flatMap { _ =>
      answers.remove(IsNewReturn) match {
        case Success(userAnswersWithIsNewReturnRemoved) =>
          userAnswersCacheConnector
            .save(request.internalId, userAnswersWithIsNewReturnRemoved.data)
            .map(_ => ())
        case Failure(ex) => throw ex
      }
    }
  }

  def getAFTDetails(pstr: String, startDate: String, aftVersion: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[JsValue] =
    aftConnector.getAFTDetails(pstr, startDate, aftVersion)
}
