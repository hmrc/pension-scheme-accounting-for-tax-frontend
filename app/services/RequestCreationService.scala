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
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import connectors.AFTConnector
import connectors.MinimalPsaConnector
import javax.inject.Singleton
import models.AFTOverview
import models.AccessMode
import models.LocalDateBinder._
import models.Quarters
import models.SchemeStatus.statusByName
import models.SessionAccessData
import models.requests.OptionalDataRequest
import models.SchemeDetails
import models.SessionData
import models.UserAnswers
import pages.PSANameQuery
import play.api.libs.json._
import uk.gov.hmrc.http.HeaderCarrier
import utils.DateHelper

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class RequestCreationService @Inject()(
    aftConnector: AFTConnector,
    userAnswersCacheConnector: UserAnswersCacheConnector,
    schemeService: SchemeService,
    minimalPsaConnector: MinimalPsaConnector,
    config: FrontendAppConfig
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
      val optionUA = data.map(jsValue => UserAnswers(jsValue.as[JsObject]))
      OptionalDataRequest[A](request, id, psaId, optionUA, sessionData.getOrElse(throw new RuntimeException("No session data found")))
    }
  }

  def emptySessionData = SessionData("", None, SessionAccessData(0, AccessMode.PageAccessModeViewOnly))

  def retrieveAndCreateRequest[A](psaId: PsaId, srn: String, startDate: LocalDate, optionVersion: Option[String])(
      implicit request: Request[A],
      executionContext: ExecutionContext,
      headerCarrier: HeaderCarrier): Future[OptionalDataRequest[A]] = {
    val id = s"$srn$startDate"

    def optionalDataRequest(optionJsValue:Option[JsValue]): OptionalDataRequest[A] = {
      val optionUA = optionJsValue.map { jsValue =>
        UserAnswers(jsValue.as[JsObject])
      }
      OptionalDataRequest[A](request, id, psaId, optionUA, emptySessionData)
    }

    userAnswersCacheConnector.fetch(id).flatMap { data =>
      val tuple = retrieveAFTRequiredDetails(srn, startDate, optionVersion)(implicitly, implicitly, optionalDataRequest(data))
      tuple.flatMap {
        case (_, ua) =>
          userAnswersCacheConnector.getSessionData(id).map {
            case Some(sd) => OptionalDataRequest[A](request, id, psaId, Some(ua), sd)
            case _ => throw new RuntimeException("No session data found")
          }
      }
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

  private def createSessionAccessData(optionVersion: Option[String], seqAFTOverview: Seq[AFTOverview], isLocked: Boolean, psaSuspended: Boolean) = {
    val maxVersion = seqAFTOverview.headOption.map(_.numberOfVersions).getOrElse(0)
    val optionVersionAsInt = optionVersion.map(_.toInt)

    val viewOnly = isLocked || psaSuspended || optionVersionAsInt.exists(_ < maxVersion)
    val anyVersions = seqAFTOverview.nonEmpty
    val isInCompile = seqAFTOverview.headOption.exists(_.compiledVersionAvailable)

    val (version, accessMode) =
      (viewOnly, anyVersions, isInCompile) match {
        case (true, false, _)    => (1, AccessMode.PageAccessModeViewOnly)
        case (true, true, _)     => (optionVersionAsInt.getOrElse(maxVersion), AccessMode.PageAccessModeViewOnly)
        case (false, true, true) => (maxVersion, AccessMode.PageAccessModeCompile)
        case _                   => (maxVersion + 1, AccessMode.PageAccessModePreCompile)
      }

    SessionAccessData(version, accessMode)
  }

  private def save(ua: UserAnswers, srn: String, startDate: LocalDate, optionVersion: Option[String], pstr: String)(
      implicit request: OptionalDataRequest[_],
      hc: HeaderCarrier,
      ec: ExecutionContext): Future[UserAnswers] = {
    def saveAll(optionLockedBy: Option[String],
                seqAFTOverview: Seq[AFTOverview])(implicit request: OptionalDataRequest[_], hc: HeaderCarrier, ec: ExecutionContext) = {
      val sad: SessionAccessData =
        createSessionAccessData(optionVersion, seqAFTOverview, optionLockedBy.isDefined, ua.get(IsPsaSuspendedQuery).getOrElse(true))

      userAnswersCacheConnector
        .save(
          request.internalId,
          ua.data,
          optionSessionData = Some(sad),
          lockReturn = sad.accessMode != AccessMode.PageAccessModeViewOnly
        )
    }

    val id = s"$srn$startDate"

    for {
      optionLockedBy <- userAnswersCacheConnector.lockedBy(id)
      seqAFTOverview <- getAftOverview(pstr, startDate)
      savedJson <- saveAll(optionLockedBy, seqAFTOverview)
    } yield {
      UserAnswers(savedJson.as[JsObject])
    }
  }

  private def isOverviewApiDisabled: Boolean =
    LocalDate.parse(config.overviewApiEnablementDate).isAfter(DateHelper.today)

  private def getAftOverview(pstr: String, startDate: LocalDate)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[AFTOverview]] = {
    val endDate: LocalDate = Quarters.getQuarter(startDate).endDate
    if (isOverviewApiDisabled) {
      aftConnector
        .getListOfVersions(pstr, startDate)
        .map { aftVersion =>
          aftVersion.map { _ =>
            AFTOverview(
              periodStartDate = startDate,
              periodEndDate = endDate,
              numberOfVersions = 1,
              submittedVersionAvailable = false,
              compiledVersionAvailable = true
            )
          }
        }
    } else { // After 1st July
      aftConnector.getAftOverview(
        pstr,
        optionStartDate = Some(startDate),
        optionEndDate = Some(endDate)
      )
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
              .setOrException(QuarterPage, Quarters.getQuarter(startDate))
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
              .setOrException(PSANameQuery, psaDetails.name)

          }
        case Some(_) =>
          Future.successful(uaWithStatus)
      }
    }
  }
}
