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
import pages.QuestionPage
import play.api.mvc.AnyContent
import uk.gov.hmrc.http.HeaderCarrier
import utils.DeleteChargeHelper

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DeleteAFTChargeService @Inject()(
    aftConnector: AFTConnector,
    userAnswersCacheConnector: UserAnswersCacheConnector,
    deleteChargeHelper: DeleteChargeHelper,
    userAnswersService: UserAnswersService
) {

  def deleteAndFileAFTReturn[A](pstr: String, answers: UserAnswers, page: Option[QuestionPage[A]] = None)(
      implicit ec: ExecutionContext,
      hc: HeaderCarrier,
      request: DataRequest[AnyContent]): Future[Unit] = {
    val isDeletingLastCharge = deleteChargeHelper.hasLastChargeOnly(answers)
    val isAmendment = request.sessionData.sessionAccessData.version > 1
    println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> isDeletingLastCharge "+isDeletingLastCharge)
    println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> isAmendment "+isAmendment)

    val updateAnswers = if (isAmendment) {
      page.map(removePage => userAnswersService.remove(removePage)).getOrElse(answers)
    } else {
      if (isDeletingLastCharge) {
        deleteChargeHelper.zeroOutLastCharge(answers)
      } else {
        page.map(noChargePath => answers.removeWithPath(noChargePath.path)).getOrElse(answers)
      }
    }

    aftConnector.fileAFTReturn(pstr, updateAnswers).flatMap { _ =>
      if (isDeletingLastCharge && !isAmendment) {
        userAnswersCacheConnector.removeAll(request.internalId).map(_ => ())
      } else {
        userAnswersCacheConnector.save(request.internalId, updateAnswers.data).map(_ => ())
      }
    }
  }
}
