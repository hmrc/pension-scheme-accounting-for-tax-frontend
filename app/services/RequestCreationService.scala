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
import models.{AFTOverview, AccessMode, AccessType, Draft, Quarters, SchemeDetails, SessionAccessData, UserAnswers}
import pages._
import play.api.libs.json._
import play.api.mvc.Request
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


  private def isPreviousPageWithinAFT(implicit request: Request[_]): Boolean =
    request.headers.get("Referer").getOrElse("").contains("manage-pension-scheme-accounting-for-tax")

  def retrieveAndCreateRequest[A](srn: String, startDate: LocalDate, version: Int, accessType: AccessType, optionCurrentPage: Option[Page])(
    implicit request: IdentifierRequest[A],
    executionContext: ExecutionContext,
    headerCarrier: HeaderCarrier): Future[OptionalDataRequest[A]] = {

    val id = s"$srn$startDate"

   userAnswersCacheConnector.fetch(id).flatMap { data =>
      (data, version, accessType, optionCurrentPage, isPreviousPageWithinAFT) match {
        case (None, 1, Draft, Some(AFTSummaryPage), true) =>
          Future.successful(OptionalDataRequest[A](request, id, request.psaId, None, None))
        case _ =>
          val optionUA = data.map { jsValue => UserAnswers(jsValue.as[JsObject])}
          retrieveAFTRequiredDetails(srn, startDate, version, accessType, optionUA)
      }
    }
  }

  private def retrieveAFTRequiredDetails[A](srn: String, startDate: LocalDate, version: Int,
                                         accessType: AccessType,
                                         ua: Option[UserAnswers])(
                                        implicit request: IdentifierRequest[A], hc: HeaderCarrier, ec: ExecutionContext): Future[OptionalDataRequest[A]] = {
    val id = s"$srn$startDate"
    val psaId = request.psaId.id
    for {
      schemeDetails <- schemeService.retrieveSchemeDetails(psaId, srn)
      seqAFTOverview <- getAftOverview(schemeDetails.pstr, startDate)
      uaWithMinPsaDetails <- updateMinimalPsaDetailsInUa(ua.getOrElse(UserAnswers()), schemeDetails.schemeStatus, psaId)
      updatedUA <- updateUserAnswersWithAFTDetails(version, schemeDetails, startDate, accessType, uaWithMinPsaDetails, seqAFTOverview)
      sessionAccessData <- createSessionAccessData(version, seqAFTOverview, updatedUA, srn, startDate)
      userAnswers <- userAnswersCacheConnector.saveAndLock(id, updatedUA.data, sessionAccessData,
        lockReturn = sessionAccessData.accessMode != AccessMode.PageAccessModeViewOnly)
      sessionData <- userAnswersCacheConnector.getSessionData(id)
    } yield {
      OptionalDataRequest[A](request, id, request.psaId, Some(UserAnswers(userAnswers.as[JsObject])), sessionData)
    }
  }


  private def createSessionAccessData(versionInt: Int, seqAFTOverview: Seq[AFTOverview], ua: UserAnswers, srn: String, startDate: LocalDate)
                                     (implicit hc: HeaderCarrier, ec: ExecutionContext) : Future[SessionAccessData] = {
    val psaSuspended = ua.get(IsPsaSuspendedQuery).getOrElse(true)

    userAnswersCacheConnector.lockedBy(srn, startDate).map { lockedBy =>
      val maxVersion = seqAFTOverview.headOption.map(_.numberOfVersions).getOrElse(0)
      val viewOnly = lockedBy.isDefined || psaSuspended || versionInt < maxVersion
      val anyVersions = seqAFTOverview.nonEmpty
      val isInCompile = seqAFTOverview.headOption.exists(_.compiledVersionAvailable)


      val areSubmittedVersionsAvailable = seqAFTOverview.headOption.exists(_.submittedVersionAvailable)

      val (version, accessMode) =
        (viewOnly, anyVersions, isInCompile) match {
          case (true, _, _) => (versionInt, AccessMode.PageAccessModeViewOnly)
          case (false, true, true) => (maxVersion, AccessMode.PageAccessModeCompile)
          case _ => (maxVersion + 1, AccessMode.PageAccessModePreCompile)
        }

      SessionAccessData(version, accessMode, areSubmittedVersionsAvailable)
    }
  }

  private def getAftOverview(pstr: String, startDate: LocalDate)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[AFTOverview]] = {

    if (LocalDate.parse(config.overviewApiEnablementDate).isAfter(DateHelper.today)) {
      Future.successful(Seq(AFTOverview(
        periodStartDate = startDate,
        periodEndDate = Quarters.getQuarter(startDate).endDate,
        numberOfVersions = 1,
        submittedVersionAvailable = false,
        compiledVersionAvailable = true
      )))
    } else { // After 21st July
      aftConnector.getAftOverview(pstr, Some(startDate), Some(Quarters.getQuarter(startDate).endDate))
    }
  }

  private def updateMinimalPsaDetailsInUa(ua: UserAnswers, schemeStatus: String, psaId: String)
                                         (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[UserAnswers] = {
    val uaWithStatus = ua.setOrException(SchemeStatusQuery, statusByName(schemeStatus))
    uaWithStatus.get(IsPsaSuspendedQuery) match {
      case None =>
        minimalPsaConnector.getMinimalPsaDetails(psaId).map { psaDetails =>
          uaWithStatus
            .setOrException(IsPsaSuspendedQuery, psaDetails.isPsaSuspended)
            .setOrException(PSAEmailQuery, psaDetails.email)
            .setOrException(PSANameQuery, psaDetails.name)

        }
      case Some(_) =>
        Future.successful(uaWithStatus)
    }
  }

  private def updateUserAnswersWithAFTDetails(version: Int, schemeDetails: SchemeDetails, startDate: LocalDate,
                                              accessType: AccessType, ua: UserAnswers,
                                              seqAFTOverview: Seq[AFTOverview])(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[UserAnswers] = {

      if (ua.get(IsPsaSuspendedQuery).contains(true) | seqAFTOverview.isEmpty) {
        Future.successful(
          (ua.setOrException(QuarterPage, Quarters.getQuarter(startDate))
            .setOrException(AFTStatusQuery, value = "Compiled")
            .setOrException(SchemeNameQuery, schemeDetails.schemeName)
            .setOrException(PSTRQuery, schemeDetails.pstr)))
      } else {
        val isCompilable = seqAFTOverview.headOption.map(_.compiledVersionAvailable)

        val updatedVersion = (accessType, isCompilable) match {
          case (Draft, Some(false)) => version - 1
          case _ => version
        }

        aftConnector
          .getAFTDetails(schemeDetails.pstr, startDate, updatedVersion.toString)
          .map(aftDetails => (UserAnswers(ua.data ++ aftDetails.as[JsObject])))
      }
  }
}
