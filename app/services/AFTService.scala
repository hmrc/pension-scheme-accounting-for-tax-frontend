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

import java.time.LocalDate

import com.google.inject.Inject
import connectors.cache.UserAnswersCacheConnector
import connectors.AFTConnector
import javax.inject.Singleton
import models.requests.DataRequest
import models.UserAnswers
import uk.gov.hmrc.http.HeaderCarrier
import utils.DateHelper

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class AFTService @Inject()(
    aftConnector: AFTConnector,
    userAnswersCacheConnector: UserAnswersCacheConnector,
    aftReturnTidyService: AFTReturnTidyService
) {


  def fileAFTReturn(pstr: String, answers: UserAnswers)(implicit ec: ExecutionContext, hc: HeaderCarrier, request: DataRequest[_]): Future[Unit] = {

    val hasDeletedLastMemberOrEmployerFromLastCharge = !aftReturnTidyService.isAtLeastOneValidCharge(answers)

    val ua = if (hasDeletedLastMemberOrEmployerFromLastCharge) {
      aftReturnTidyService.reinstateDeletedMemberOrEmployer(answers)
    } else {
      aftReturnTidyService.removeChargesHavingNoMembersOrEmployers(answers)
    }

    aftConnector.fileAFTReturn(pstr, ua).flatMap { _ =>
      if (hasDeletedLastMemberOrEmployerFromLastCharge) {
        userAnswersCacheConnector.removeAll(request.internalId).map(_ => ())
      } else {
        Future.successful(())
      }
    }
  }

  def isSubmissionDisabled(quarterEndDate: String): Boolean = {
    val nextDay = LocalDate.parse(quarterEndDate).plusDays(1)
    !(DateHelper.today.compareTo(nextDay) >= 0)
  }
}
