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

import java.time.{LocalDateTime, LocalDate, LocalTime}

import com.google.inject.Inject
import connectors.cache.UserAnswersCacheConnector
import connectors.{AFTConnector, MinimalPsaConnector}
import javax.inject.Singleton
import models.AFTOverview
import models.AccessMode
import models.LocalDateBinder._
import models.SchemeStatus.statusByName
import models.SessionData
import models.SessionAccessData
import models.requests.{DataRequest, OptionalDataRequest}
import models.{SchemeDetails, StartQuarters, UserAnswers}
import pages._
import play.api.libs.json._
import uk.gov.hmrc.http.HeaderCarrier
import utils.DateHelper

import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Success, Failure}

@Singleton
class AFTService @Inject()(
    aftConnector: AFTConnector,
    userAnswersCacheConnector: UserAnswersCacheConnector,
    schemeService: SchemeService,
    minimalPsaConnector: MinimalPsaConnector,
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
        ua.remove(IsNewReturn) match {
          case Success(userAnswersWithIsNewReturnRemoved) =>
            userAnswersCacheConnector.save(request.internalId, userAnswersWithIsNewReturnRemoved.data).map(_ => ())
          case Failure(ex) => throw ex
        }
      }
    }
  }

  def isSubmissionDisabled(quarterEndDate: String): Boolean = {
    val nextDay = LocalDate.parse(quarterEndDate).plusDays(1)
    !(DateHelper.today.compareTo(nextDay) >= 0)
  }
}
