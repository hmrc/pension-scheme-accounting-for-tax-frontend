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

package services

import java.time.LocalDate

import com.google.inject.Inject
import connectors.AFTConnector
import javax.inject.Singleton
import models.requests.DataRequest
import uk.gov.hmrc.http.HeaderCarrier
import utils.DateHelper
import models.{UserAnswers, JourneyType}
import pages.AFTStatusQuery

import scala.concurrent.{Future, ExecutionContext}

@Singleton
class AFTService @Inject()(
    aftConnector: AFTConnector
) {

  def fileSubmitReturn(pstr: String, answers: UserAnswers)(implicit ec: ExecutionContext, hc: HeaderCarrier, request: DataRequest[_]): Future[Unit] = {
    val journeyType = if (request.isAmendment) JourneyType.AFT_SUBMIT_AMEND else JourneyType.AFT_SUBMIT_RETURN
    aftConnector
      .fileAFTReturn(pstr, answers.setOrException(AFTStatusQuery, "Submitted"), journeyType)
  }

  def fileCompileReturn(pstr: String, answers: UserAnswers)(implicit ec: ExecutionContext, hc: HeaderCarrier, request: DataRequest[_]): Future[Unit] = {
    val journeyType = if (request.isAmendment) JourneyType.AFT_COMPILE_AMEND else JourneyType.AFT_COMPILE_RETURN
    aftConnector
      .fileAFTReturn(pstr, answers.setOrException(AFTStatusQuery, "Compiled"), journeyType)
  }

  def isSubmissionDisabled(quarterEndDate: String): Boolean = {
    val nextDay = LocalDate.parse(quarterEndDate).plusDays(1)
    !(DateHelper.today.compareTo(nextDay) >= 0)
  }
}
