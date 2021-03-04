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
import connectors.cache.UserAnswersCacheConnector
import connectors.{MinimalConnector, AFTConnector}

import javax.inject.Singleton
import models.LocalDateBinder._
import models.SchemeStatus.statusByName
import models.requests.{IdentifierRequest, OptionalDataRequest}
import models.{AFTOverview, SessionAccessData, Quarters, Draft, SchemeDetails, AccessMode, UserAnswers, MinimalFlags, AccessType}
import pages._
import play.api.libs.json._
import play.api.mvc.Request
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RequestCreationService @Inject()(
                                        aftConnector: AFTConnector,
                                        userAnswersCacheConnector: UserAnswersCacheConnector,
                                        schemeService: SchemeService,
                                        minimalConnector: MinimalConnector
                                      ) {


  private def isPreviousPageWithinAFT(implicit request: Request[_]): Boolean =
    request.headers.get("Referer").getOrElse("").contains("manage-pension-scheme-accounting-for-tax")

  def retrieveAndCreateRequest[A](
                                   srn: String,
                                   startDate: LocalDate,
                                   version: Int,
                                   accessType: AccessType,
                                   optionCurrentPage: Option[Page]
                                 )(
                                   implicit request: IdentifierRequest[A],
                                   executionContext: ExecutionContext,
                                   headerCarrier: HeaderCarrier
                                 ): Future[OptionalDataRequest[A]] = {

    val id = s"$srn$startDate"

    userAnswersCacheConnector.fetch(id).flatMap { data =>
      (data, version, accessType, optionCurrentPage, isPreviousPageWithinAFT) match {
        case (None, 1, Draft, Some(AFTSummaryPage), true) =>
          Future.successful(OptionalDataRequest[A](request, id, request.psaId, request.pspId, None, None))
        case _ =>
          val optionUA = data.map { jsValue => UserAnswers(jsValue.as[JsObject]) }
          retrieveAFTRequiredDetails(srn, startDate, version, accessType, optionUA)
      }
    }
  }

  private def retrieveAFTRequiredDetails[A](
                                             srn: String,
                                             startDate: LocalDate,
                                             version: Int,
                                             accessType: AccessType,
                                             ua: Option[UserAnswers]
                                           )(
                                             implicit request: IdentifierRequest[A],
                                             hc: HeaderCarrier,
                                             ec: ExecutionContext
                                           ): Future[OptionalDataRequest[A]] = {
    val id = s"$srn$startDate"
    val psaId = request.idOrException

    for {
      schemeDetails <- schemeService.retrieveSchemeDetails(psaId, srn, "srn")
      seqAFTOverview <- aftConnector.getAftOverview(schemeDetails.pstr, Some(startDate), Some(Quarters.getQuarter(startDate).endDate))
      uaWithMinPsaDetails <- updateMinimalDetailsInUa(ua.getOrElse(UserAnswers()), schemeDetails.schemeStatus)
      updatedUA <- updateUserAnswersWithAFTDetails(version, schemeDetails, startDate, accessType, uaWithMinPsaDetails, seqAFTOverview)
      sessionAccessData <- createSessionAccessData(version, seqAFTOverview, srn, startDate)
      userAnswers <- userAnswersCacheConnector.saveAndLock(
        id = id,
        value = updatedUA.data,
        sessionAccessData = sessionAccessData,
        lockReturn = sessionAccessData.accessMode != AccessMode.PageAccessModeViewOnly
      )
      sessionData <- userAnswersCacheConnector.getSessionData(id)
    } yield {
      OptionalDataRequest[A](request, id, request.psaId, request.pspId, Some(UserAnswers(userAnswers.as[JsObject])), sessionData)
    }
  }


  private def createSessionAccessData[A](
                                          versionInt: Int,
                                          seqAFTOverview: Seq[AFTOverview],
                                          srn: String,
                                          startDate: LocalDate
                                        )(
                                          implicit hc: HeaderCarrier,
                                          ec: ExecutionContext
                                        ): Future[SessionAccessData] = {
    userAnswersCacheConnector.lockDetail(srn, startDate).map { optionLockDetail =>
      val maxVersion = seqAFTOverview.headOption.map(_.numberOfVersions).getOrElse(0)
      val viewOnly = optionLockDetail.isDefined || versionInt < maxVersion
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

  private def updateMinimalDetailsInUa[A](
                                              ua: UserAnswers,
                                              schemeStatus: String
                                            )(
                                              implicit hc: HeaderCarrier,
                                              ec: ExecutionContext,
                                              request: IdentifierRequest[A]
                                            ): Future[UserAnswers] = {
    minimalConnector.getMinimalDetails.map { minimalDetails =>
      ua.setOrException(SchemeStatusQuery, statusByName(schemeStatus))
        .setOrException(EmailQuery, minimalDetails.email)
        .setOrException(NameQuery, minimalDetails.name)
        .setOrException(MinimalFlagsQuery, MinimalFlags(minimalDetails.deceasedFlag, minimalDetails.rlsFlag))
    }
  }

  private def updateUserAnswersWithAFTDetails(version: Int, schemeDetails: SchemeDetails, startDate: LocalDate,
                                              accessType: AccessType, ua: UserAnswers,
                                              seqAFTOverview: Seq[AFTOverview])(
                                               implicit hc: HeaderCarrier,
                                               ec: ExecutionContext): Future[UserAnswers] = {

    if (seqAFTOverview.isEmpty) {
      Future.successful(
        ua.setOrException(QuarterPage, Quarters.getQuarter(startDate))
          .setOrException(AFTStatusQuery, value = "Compiled")
          .setOrException(SchemeNameQuery, schemeDetails.schemeName)
          .setOrException(PSTRQuery, schemeDetails.pstr))
    } else {
      val isCompilable = seqAFTOverview.headOption.map(_.compiledVersionAvailable)

      val updatedVersion = (accessType, isCompilable) match {
        case (Draft, Some(false)) => version - 1
        case _ => version
      }

      aftConnector
        .getAFTDetails(schemeDetails.pstr, startDate, updatedVersion.toString)
        .map(aftDetails => UserAnswers(ua.data ++ aftDetails.as[JsObject]))
    }
  }
}
