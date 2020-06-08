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
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import connectors.{AFTConnector, MinimalPsaConnector}
import javax.inject.Singleton
import models.LocalDateBinder._
import models.SchemeStatus.statusByName
import models.requests.{IdentifierRequest, OptionalDataRequest}
import models.{AFTOverview, AccessMode, AccessType, Draft, Quarters, SchemeDetails, SessionAccessData, Submission, UserAnswers}
import pages._
import play.api.libs.json._
import play.api.mvc.Request
import uk.gov.hmrc.domain.PsaId
import uk.gov.hmrc.http.HeaderCarrier
import utils.DateHelper

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RequestCreationService @Inject()(
    aftConnector: AFTConnector,
    userAnswersCacheConnector: UserAnswersCacheConnector,
    schemeService: SchemeService,
    minimalPsaConnector: MinimalPsaConnector,
    config: FrontendAppConfig
) {
  def createRequest[A](psaId: PsaId, srn: String, startDate: LocalDate)(implicit request: Request[A],
                                                                        executionContext: ExecutionContext,
                                                                        headerCarrier: HeaderCarrier): Future[OptionalDataRequest[A]] = {
    val id = s"$srn$startDate"
    for {
      data <- userAnswersCacheConnector.fetch(id)
      sessionData <- userAnswersCacheConnector.getSessionData(id)
    } yield {
      val optionUA = data.map(jsValue => UserAnswers(jsValue.as[JsObject]))
      OptionalDataRequest[A](request, id, psaId, optionUA, sessionData)
    }
  }

  private def isPreviousPageWithinAFT(implicit request: Request[_]): Boolean =
    request.headers.get("Referer").getOrElse("").contains("manage-pension-scheme-accounting-for-tax")

  def retrieveAndCreateRequest[A](srn: String, startDate: LocalDate, version: Int, accessType: AccessType, optionCurrentPage: Option[Page])(
      implicit request: IdentifierRequest[A],
      executionContext: ExecutionContext,
      headerCarrier: HeaderCarrier): Future[OptionalDataRequest[A]] = {

    val id = s"$srn$startDate"

    def optionalDataRequest(optionJsValue: Option[JsValue]): OptionalDataRequest[A] = {
      val optionUA = optionJsValue.map { jsValue => UserAnswers(jsValue.as[JsObject])
      }
      OptionalDataRequest[A](request, id, request.psaId, optionUA, None)
    }

    userAnswersCacheConnector.fetch(id).flatMap { data =>
      (data, version, accessType, optionCurrentPage, isPreviousPageWithinAFT) match {
        case (None, 1, Draft, Some(AFTSummaryPage), true) =>
          Future.successful(optionalDataRequest(None))
        case _ =>
          val tuple = retrieveAFTRequiredDetails(srn, startDate, version, accessType)(implicitly, implicitly, optionalDataRequest(data))

          tuple.flatMap {
            case (_, ua) =>
              userAnswersCacheConnector.getSessionData(id).map { sd => OptionalDataRequest[A](request, id, request.psaId, Some(ua), sd)
              }
          }
      }
    }
  }

  private def retrieveAFTRequiredDetails(srn: String, startDate: LocalDate, optionVersion: Int, accessType: AccessType)(
      implicit hc: HeaderCarrier,
      ec: ExecutionContext,
      request: OptionalDataRequest[_]): Future[(SchemeDetails, UserAnswers)] = {
    for {
      schemeDetails <- schemeService.retrieveSchemeDetails(request.psaId.id, srn)
      (updatedUA, sessionData) <- updateUserAnswersWithAFTDetails(optionVersion, schemeDetails, startDate, srn)
      savedUA <- save(updatedUA, sessionData)
    } yield {
      (schemeDetails, savedUA)
    }
  }

  private def createSessionAccessData(versionInt: Int, seqAFTOverview: Seq[AFTOverview], isLocked: Boolean, psaSuspended: Boolean): SessionAccessData = {
    val maxVersion = seqAFTOverview.headOption.map(_.numberOfVersions).getOrElse(0)
    val viewOnly = isLocked || psaSuspended || versionInt < maxVersion
    val anyVersions = seqAFTOverview.nonEmpty
    val isInCompile = seqAFTOverview.headOption.exists(_.compiledVersionAvailable)

    val (version, accessMode) =
      (viewOnly, anyVersions, isInCompile) match {
        case (true, false, _)    => (1, AccessMode.PageAccessModeViewOnly)
        case (true, true, _)     => (versionInt, AccessMode.PageAccessModeViewOnly)
        case (false, true, true) => (maxVersion, AccessMode.PageAccessModeCompile)
        case _                   => (maxVersion + 1, AccessMode.PageAccessModePreCompile)
      }

    SessionAccessData(version, accessMode)
  }

  private def save(ua: UserAnswers,
                   sad: SessionAccessData)(implicit request: OptionalDataRequest[_], hc: HeaderCarrier, ec: ExecutionContext): Future[UserAnswers] = {
    val savedJsValue = userAnswersCacheConnector.save(request.internalId, ua.data, optionSessionData = Some(sad),
      lockReturn = sad.accessMode != AccessMode.PageAccessModeViewOnly)

    savedJsValue.map(json => UserAnswers(json.as[JsObject]))
  }

  private def isOverviewApiDisabled: Boolean =
    LocalDate.parse(config.overviewApiEnablementDate).isAfter(DateHelper.today)

  private def getAftOverview(pstr: String, startDate: LocalDate)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[AFTOverview]] = {

    if (isOverviewApiDisabled) {
      aftConnector
        .getListOfVersions(pstr, startDate)
        .map { aftVersion =>
          aftVersion.map { _ =>
            AFTOverview(
              periodStartDate = startDate,
              periodEndDate = Quarters.getQuarter(startDate).endDate,
              numberOfVersions = 1,
              submittedVersionAvailable = false,
              compiledVersionAvailable = true
            )
          }
        }
    } else { // After 1st July
      aftConnector.getAftOverview(pstr, Some(startDate), Some(Quarters.getQuarter(startDate).endDate))
    }
  }

  private def updateUserAnswersWithAFTDetails(version: Int, schemeDetails: SchemeDetails, startDate: LocalDate, srn: String)(
      implicit hc: HeaderCarrier,
      ec: ExecutionContext,
      request: OptionalDataRequest[_]): Future[(UserAnswers, SessionAccessData)] = {

    def updateMinimalPsaDetailsInUa(ua: UserAnswers): Future[UserAnswers] = {
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

    val uaWithSessionData = for {
      optionLockedBy <- userAnswersCacheConnector.lockedBy(srn, startDate)
      seqAFTOverview <- getAftOverview(schemeDetails.pstr, startDate)
      ua <- updateMinimalPsaDetailsInUa(request.userAnswers.getOrElse(UserAnswers()))
    } yield {
      val sessionData = createSessionAccessData(version, seqAFTOverview, optionLockedBy.isDefined,
        ua.get(IsPsaSuspendedQuery).getOrElse(true))
      if (ua.get(IsPsaSuspendedQuery).contains(true) | seqAFTOverview.isEmpty) {
        Future.successful(
          (ua.setOrException(QuarterPage, Quarters.getQuarter(startDate))
             .setOrException(AFTStatusQuery, value = "Compiled")
             .setOrException(SchemeNameQuery, schemeDetails.schemeName)
             .setOrException(PSTRQuery, schemeDetails.pstr),
           sessionData))
      } else {
        aftConnector
          .getAFTDetails(schemeDetails.pstr, startDate, version.toString)
          .map(aftDetails => (UserAnswers(ua.data ++ aftDetails.as[JsObject]), sessionData))
      }
    }
    uaWithSessionData.flatMap(identity)
  }
}
