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
import connectors.{AFTConnector, ReturnAlreadySubmittedException}
import models.requests.DataRequest
import models.{JourneyType, UserAnswers}
import pages.AFTStatusQuery
import uk.gov.hmrc.http.HeaderCarrier
import utils.DateHelper

import java.time.LocalDate
import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AFTService @Inject()(
    aftConnector: AFTConnector
) {

  def fileSubmitReturn(pstr: String, answers: UserAnswers, srn: String)(implicit ec: ExecutionContext, hc: HeaderCarrier,
                                                                        request: DataRequest[?]): Future[Unit] = {
    val journeyType = if (request.isAmendment) JourneyType.AFT_SUBMIT_AMEND else JourneyType.AFT_SUBMIT_RETURN
    aftConnector
      .fileAFTReturn(pstr, answers.setOrException(AFTStatusQuery, "Submitted"), journeyType, srn, request.isLoggedInAsPsa)
  }

  def fileCompileReturn(pstr: String, answers: UserAnswers, srn: String)(implicit ec: ExecutionContext,
                                                                         hc: HeaderCarrier, request: DataRequest[?]): Future[Unit] = {
    val journeyType = if (request.isAmendment) JourneyType.AFT_COMPILE_AMEND else JourneyType.AFT_COMPILE_RETURN
    aftConnector
      .fileAFTReturn(pstr, answers.setOrException(AFTStatusQuery, "Compiled"), journeyType, srn, request.isLoggedInAsPsa)
      .recover {
        case _: ReturnAlreadySubmittedException => ()
      }
  }

  def isSubmissionDisabled(quarterEndDate: String): Boolean = {
    val nextDay = LocalDate.parse(quarterEndDate).plusDays(1)
    !(DateHelper.today.compareTo(nextDay) >= 0)
  }
}
