/*
 * Copyright 2023 HM Revenue & Customs
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
import connectors.cache.UserAnswersCacheConnector
import connectors.{AFTConnector, MinimalConnector}
import models.LocalDateBinder._
import models.SchemeStatus.statusByName
import models.requests.{IdentifierRequest, OptionalDataRequest}
import models.{AFTOverviewOnPODS, AccessMode, AccessType, Draft, MinimalFlags, Quarters, SchemeDetails, SessionAccessData, UserAnswers}
import pages._
import play.api.Logger
import play.api.libs.json._
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDate
import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RequestCreationService @Inject()(
                                        aftConnector: AFTConnector,
                                        userAnswersCacheConnector: UserAnswersCacheConnector,
                                        schemeService: SchemeService,
                                        minimalConnector: MinimalConnector
                                      ) {

  private val logger = Logger(classOf[RequestCreationService])

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
    logger.info("Entered retrieveAndCreateRequest method 1")
    userAnswersCacheConnector.fetch(id).flatMap { data =>

          val optionUA = data.map { jsValue => UserAnswers(jsValue.as[JsObject]) }
          if(optionUA.nonEmpty) {
            logger.info(s"Some data found in cache for ${optionCurrentPage.getOrElse("unrecognised page")}")
          } else {
            logger.warn(s"No data found in cache but user in default case for ${optionCurrentPage.getOrElse("unrecognised page")}")
          }
      retrieveAFTRequiredDetails(srn, startDate, version, accessType, optionUA)

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
      seqAFTOverviewPODS = seqAFTOverview.filter(_.versionDetails.isDefined).map(_.toPodsReport)
      updatedUA <- updateUserAnswersWithAFTDetails(version, schemeDetails, startDate, accessType, uaWithMinPsaDetails, seqAFTOverviewPODS)
      sessionAccessData <- createSessionAccessData(version, seqAFTOverviewPODS, srn, startDate)
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
                                          seqAFTOverview: Seq[AFTOverviewOnPODS],
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
                                              seqAFTOverview: Seq[AFTOverviewOnPODS])(
                                               implicit hc: HeaderCarrier,
                                               ec: ExecutionContext): Future[UserAnswers] = {

    if (seqAFTOverview.isEmpty) {
      logger.warn("seqAFTOverview is empty - getAftDetails will not be called")
      Future.successful(
        ua.setOrException(QuarterPage, Quarters.getQuarter(startDate))
          .setOrException(AFTStatusQuery, value = "Compiled")
          .setOrException(SchemeNameQuery, schemeDetails.schemeName)
          .setOrException(PSTRQuery, schemeDetails.pstr))
    } else {
      logger.info("seqAFTOverview non empty - getAftDetails will be called")
      val isCompilable = seqAFTOverview.headOption.map(_.compiledVersionAvailable)

      val updatedVersion = (accessType, isCompilable) match {
        case (Draft, Some(false)) =>
          if (version == 1) {
            logger.info("Version is 1 so will derive a zero so leave at 1. seqAFTOverview=" + seqAFTOverview)
            version
          } else {
            version - 1
          }
        case _ => version
      }
      logger.info(s"seqAFTOverview non empty - getAftDetails will be called for version $updatedVersion")
      aftConnector
        .getAFTDetails(schemeDetails.pstr, startDate, updatedVersion.toString)
        .map { aftDetails =>
          UserAnswers(ua.data ++ aftDetails.as[JsObject])
        }
    }
  }
}
