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
import javax.inject.Singleton
import models.UserAnswers
import models.requests.DataRequest
import pages._
import play.api.libs.json.JsPath
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class DeleteAFTChargeService @Inject()(
    aftConnector: AFTConnector,
    userAnswersCacheConnector: UserAnswersCacheConnector,
    aftReturnTidyServiceCopy: AFTReturnTidyServiceCopy
) {

  def deleteAndFileAFTReturn(pstr: String, answers: UserAnswers, path: JsPath)
                            (implicit ec: ExecutionContext, hc: HeaderCarrier, request: DataRequest[_]): Future[Unit] = {

    println("\n\n\n here in delete: "+answers)
    val hasDeletedLastCharge = aftReturnTidyServiceCopy.hasLastChargeOnly(answers)

    println("\n\n\n hasDeletedLastCharge: "+hasDeletedLastCharge)
    val ua = if (aftReturnTidyServiceCopy.hasLastChargeOnly(answers)) {
      aftReturnTidyServiceCopy.zeroOutLastCharge(answers)
    } else {
      println("\n\n\n answers : "+answers)
      answers.removeWithPath(path)
    }
println("\n\n\n ua : "+ua)
    aftConnector.fileAFTReturn(pstr, ua).flatMap { _ =>
      if (hasDeletedLastCharge) {
        userAnswersCacheConnector.removeAll(request.internalId).map(_ => ())
      } else {
        ua.remove(IsNewReturn) match {
          case Success(userAnswersWithIsNewReturnRemoved) =>
            userAnswersCacheConnector.save(request.internalId, userAnswersWithIsNewReturnRemoved.data).map(_ => ())
          case Failure(ex) => throw ex
        }
      }
    }
  }
}
