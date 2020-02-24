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

import java.time.{LocalDate, LocalDateTime, LocalTime}

import com.google.inject.Inject
import connectors.cache.UserAnswersCacheConnector
import connectors.{AFTConnector, MinimalPsaConnector}
import javax.inject.Singleton
import models.SchemeStatus.statusByName
import models.requests.{DataRequest, OptionalDataRequest}
import models.{Quarters, SchemeDetails, UserAnswers}
import pages._
import play.api.libs.json._
import uk.gov.hmrc.http.HeaderCarrier
import utils.DateHelper

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class AFTService @Inject()(
                            aftConnector: AFTConnector,
                            userAnswersCacheConnector: UserAnswersCacheConnector,
                            schemeService: SchemeService,
                            minimalPsaConnector: MinimalPsaConnector,
                            aftReturnTidyService: AFTReturnTidyService
                          ) {

  def fileAFTReturn(pstr: String, answers: UserAnswers)(implicit ec: ExecutionContext, hc: HeaderCarrier, request: DataRequest[_]): Future[Unit] = {

    val hasDeletedLastMemberOrEmployerFromLastCharge = ! aftReturnTidyService.isAtLeastOneValidCharge(answers)

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

  def getAFTDetails(pstr: String, startDate: String, aftVersion: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[JsValue] =
    aftConnector.getAFTDetails(pstr, startDate, aftVersion)

  def retrieveAFTRequiredDetails(srn: String, startDate: String, optionVersion: Option[String])(implicit hc: HeaderCarrier, ec: ExecutionContext, request: OptionalDataRequest[_]): Future[(SchemeDetails, UserAnswers)] = {
    for {
      schemeDetails <- schemeService.retrieveSchemeDetails(request.psaId.id, srn)
      updatedUA <- updateUserAnswersWithAFTDetails(optionVersion, schemeDetails, startDate)
      savedUA <- save(updatedUA)
    } yield {
      (schemeDetails, savedUA)
    }
  }

  private def save(ua: UserAnswers)(implicit request: OptionalDataRequest[_], hc: HeaderCarrier, ec: ExecutionContext): Future[UserAnswers] = {
    val savedJson = if (request.viewOnly || ua.get(IsPsaSuspendedQuery).getOrElse(true)) {
      userAnswersCacheConnector.save(request.internalId, ua.data)
    } else {
      userAnswersCacheConnector.saveAndLock(request.internalId, ua.data)
    }
    savedJson.map(jsVal => UserAnswers(jsVal.as[JsObject]))
  }

  private def updateUserAnswersWithAFTDetails(optionVersion: Option[String], schemeDetails: SchemeDetails, startDate: String)
                                             (implicit hc: HeaderCarrier, ec: ExecutionContext, request: OptionalDataRequest[_]): Future[UserAnswers] = {
    def currentUserAnswers: UserAnswers = request.userAnswers.getOrElse(UserAnswers())

    val futureUserAnswers = optionVersion match {
      case None =>
        aftConnector.getListOfVersions(schemeDetails.pstr).map { listOfVersions =>
          if (listOfVersions.isEmpty) {
            currentUserAnswers
              .setOrException(IsNewReturn, true)
              .setOrException(QuarterPage, Quarters.getQuarter(startDate))
              .setOrException(AFTStatusQuery, value = "Compiled")
              .setOrException(SchemeNameQuery, schemeDetails.schemeName)
              .setOrException(PSTRQuery, schemeDetails.pstr)
          } else {
            currentUserAnswers
          }
        }
      case Some(version) =>
        getAFTDetails(schemeDetails.pstr, "2020-01-01", version)
          .map(aftDetails => UserAnswers(aftDetails.as[JsObject]))
    }

    futureUserAnswers.flatMap { ua =>
      val uaWithStatus = ua.setOrException(SchemeStatusQuery, statusByName(schemeDetails.schemeStatus))
      uaWithStatus.get(IsPsaSuspendedQuery) match {
        case None =>
          minimalPsaConnector.isPsaSuspended(request.psaId.id).map { retrievedIsSuspendedValue =>
            uaWithStatus.setOrException(IsPsaSuspendedQuery, retrievedIsSuspendedValue)
          }
        case Some(_) =>
          Future.successful(uaWithStatus)
      }
    }
  }

  def isSubmissionDisabled(quarterEndDate: String): Boolean = {
    val nextDay = LocalDate.parse(quarterEndDate).plusDays(1)
    !(DateHelper.today.compareTo(nextDay) >= 0)
  }
}
