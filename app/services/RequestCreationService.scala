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

import pages.AFTStatusQuery
import pages.IsNewReturn
import pages.IsPsaSuspendedQuery
import pages.PSAEmailQuery
import pages.PSTRQuery
import pages.QuarterPage
import pages.SchemeNameQuery
import pages.SchemeStatusQuery
import play.api.mvc.Request
import uk.gov.hmrc.domain.PsaId
import java.time.LocalDate

import com.google.inject.Inject
import connectors.cache.UserAnswersCacheConnector
import connectors.AFTConnector
import connectors.MinimalPsaConnector
import javax.inject.Singleton
import models.AFTOverview
import models.AccessMode
import models.LocalDateBinder._
import models.SchemeStatus.statusByName
import models.SessionAccessData
import models.requests.OptionalDataRequest
import models.SchemeDetails
import models.SessionData
import models.StartQuarters
import models.UserAnswers
import play.api.libs.json._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class RequestCreationService @Inject()(
    aftConnector: AFTConnector,
    userAnswersCacheConnector: UserAnswersCacheConnector,
    schemeService: SchemeService,
    minimalPsaConnector: MinimalPsaConnector,
    aftReturnTidyService: AFTReturnTidyService
) {
  private def getAFTDetails(pstr: String, startDate: String, aftVersion: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[JsValue] =
    aftConnector.getAFTDetails(pstr, startDate, aftVersion)

  def createRequest[A](psaId: PsaId, srn: String, startDate: LocalDate)(implicit request: Request[A],
                                                                        executionContext: ExecutionContext,
                                                                        headerCarrier: HeaderCarrier): Future[OptionalDataRequest[A]] = {
    val id = s"$srn$startDate"
    for {
      data <- userAnswersCacheConnector.fetch(id)
      sessionData <- userAnswersCacheConnector.getSessionData(id)
    } yield {
      newRequest(data.map(dd => UserAnswers(dd.as[JsObject])), sessionData, id, psaId)
    }
  }

  def retrieveAndCreateRequest[A](psaId: PsaId, srn: String, startDate: LocalDate, optionVersion: Option[String])(
      implicit request: Request[A],
      executionContext: ExecutionContext,
      headerCarrier: HeaderCarrier): Future[OptionalDataRequest[A]] = {
    val id = s"$srn$startDate"

    implicit val optionalDataRequest: OptionalDataRequest[A] = OptionalDataRequest[A](request, id, psaId, None, None)
    retrieveAFTRequiredDetails(srn, startDate, optionVersion) flatMap {
      case (_, ua) =>
        userAnswersCacheConnector.getSessionData(id).map { sd =>
          newRequest(Some(ua), sd, id, psaId)
        }
    }
  }

  private def newRequest[A](optionUserAnswers: Option[UserAnswers], sessionData: Option[SessionData], id: String, psaId: PsaId)(
      implicit request: Request[A]) = {
    (optionUserAnswers, sessionData) match {
      case (_, None) =>
        OptionalDataRequest[A](request, id, psaId, None, None)
      case (None, Some(_)) =>
        OptionalDataRequest[A](request, id, psaId, None, sessionData)
      case (Some(_), Some(_)) =>
        OptionalDataRequest[A](request, id, psaId, optionUserAnswers, sessionData)
    }
  }

  private def retrieveAFTRequiredDetails(srn: String, startDate: LocalDate, optionVersion: Option[String])(
      implicit hc: HeaderCarrier,
      ec: ExecutionContext,
      request: OptionalDataRequest[_]): Future[(SchemeDetails, UserAnswers)] = {
    for {
      schemeDetails <- schemeService.retrieveSchemeDetails(request.psaId.id, srn)
      updatedUA <- updateUserAnswersWithAFTDetails(optionVersion, schemeDetails, startDate)
      savedUA <- save(updatedUA, srn, startDate, optionVersion, schemeDetails.pstr)
    } yield {
      (schemeDetails, savedUA)
    }
  }

  private def createSessionData(optionVersion: Option[String], seqAFTOverview: Seq[AFTOverview], isLocked: Boolean, psaSuspended: Boolean) = {
    val maxVersion = seqAFTOverview.headOption.map(_.numberOfVersions).getOrElse(1)

    val version = optionVersion match {
      case None    => maxVersion
      case Some(v) => v.toInt
    }
    val accessMode = if (isLocked || psaSuspended || version < maxVersion) {
      AccessMode.PageAccessModeViewOnly
    } else {
      (seqAFTOverview.isEmpty, seqAFTOverview.headOption.exists(_.compiledVersionAvailable)) match {
        case (false, true) => AccessMode.PageAccessModeCompile
        case _             => AccessMode.PageAccessModePreCompile
      }
    }
    SessionAccessData(version, accessMode)
  }

  private def save(ua: UserAnswers, srn: String, startDate: LocalDate, optionVersion: Option[String], pstr: String)(
      implicit request: OptionalDataRequest[_],
      hc: HeaderCarrier,
      ec: ExecutionContext): Future[UserAnswers] = {
    def saveAll(optionLockedBy: Option[String],
                seqAFTOverview: Seq[AFTOverview])(implicit request: OptionalDataRequest[_], hc: HeaderCarrier, ec: ExecutionContext) = {
      val sd: SessionAccessData =
        createSessionData(optionVersion, seqAFTOverview, optionLockedBy.isDefined, ua.get(IsPsaSuspendedQuery).getOrElse(true))

      userAnswersCacheConnector
        .save(
          request.internalId,
          ua.data,
          optionSessionData = Some(sd),
          lockReturn = sd.accessMode != AccessMode.PageAccessModeViewOnly
        )
    }

    val id = s"$srn$startDate"

    for {
      optionLockedBy <- userAnswersCacheConnector.lockedBy(id)
      seqAFTOverview <- aftConnector.getAftOverview(pstr)
      savedJson <- saveAll(optionLockedBy, seqAFTOverview)
    } yield {
      UserAnswers(savedJson.as[JsObject])
    }
  }

  private def updateUserAnswersWithAFTDetails(optionVersion: Option[String], schemeDetails: SchemeDetails, startDate: LocalDate)(
      implicit hc: HeaderCarrier,
      ec: ExecutionContext,
      request: OptionalDataRequest[_]): Future[UserAnswers] = {
    def currentUserAnswers: UserAnswers = request.userAnswers.getOrElse(UserAnswers())

    val futureUserAnswers = optionVersion match {
      case None =>
        aftConnector.getListOfVersions(schemeDetails.pstr, startDate).map { listOfVersions =>
          if (listOfVersions.isEmpty) {
            currentUserAnswers
              .setOrException(IsNewReturn, true)
              .setOrException(QuarterPage, StartQuarters.getQuarter(startDate))
              .setOrException(AFTStatusQuery, value = "Compiled")
              .setOrException(SchemeNameQuery, schemeDetails.schemeName)
              .setOrException(PSTRQuery, schemeDetails.pstr)
          } else {
            currentUserAnswers
          }
        }
      case Some(version) =>
        getAFTDetails(schemeDetails.pstr, startDate, version)
          .map(aftDetails => UserAnswers(aftDetails.as[JsObject]))
    }

    futureUserAnswers.flatMap { ua =>
      val uaWithStatus = ua.setOrException(SchemeStatusQuery, statusByName(schemeDetails.schemeStatus))
      uaWithStatus.get(IsPsaSuspendedQuery) match {
        case None =>
          minimalPsaConnector.getMinimalPsaDetails(request.psaId.id).map { psaDetails =>
            uaWithStatus
              .setOrException(IsPsaSuspendedQuery, psaDetails.isPsaSuspended)
              .setOrException(PSAEmailQuery, psaDetails.email)
          }
        case Some(_) =>
          Future.successful(uaWithStatus)
      }
    }
  }
}
