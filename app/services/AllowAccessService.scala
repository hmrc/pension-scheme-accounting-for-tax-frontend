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
import connectors.{AFTConnector, SchemeDetailsConnector}
import controllers.routes._
import handlers.ErrorHandler
import models.LocalDateBinder._
import models.SchemeStatus.{Deregistered, Open, WoundUp}
import models.requests.DataRequest
import models.{SchemeStatus, UserAnswers}
import pages._
import play.api.http.Status.NOT_FOUND
import play.api.mvc.{Result, Results}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import utils.DateHelper

import scala.concurrent.{ExecutionContext, Future}

class AllowAccessService @Inject()(pensionsSchemeConnector: SchemeDetailsConnector,
                                   aftService: AFTService,
                                   aftConnector: AFTConnector,
                                   errorHandler: ErrorHandler)(
    implicit val executionContext: ExecutionContext)
    extends Results {

  private val validStatuses = Seq(Open, WoundUp, Deregistered)

  private def retrieveSuspendedFlagAndSchemeStatus(ua: UserAnswers)(
      block: (Boolean, SchemeStatus) => Future[Option[Result]]): Future[Option[Result]] = {
    (ua.get(IsPsaSuspendedQuery), ua.get(SchemeStatusQuery)) match {
      case (Some(isSuspended), Some(schemeStatus)) =>
        block(isSuspended, schemeStatus)
      case _ =>
        Future.successful(Some(Redirect(controllers.routes.SessionExpiredController.onPageLoad())))
    }
  }

  private def isPreviousPageWithinAFT(implicit request: DataRequest[_]): Boolean =
    request.headers.get("Referer").getOrElse("").contains("manage-pension-scheme-accounting-for-tax")

  def filterForIllegalPageAccess(srn: String,
                                 startDate: LocalDate,
                                 ua: UserAnswers,
                                 optPage: Option[Page] = None,
                                 optVersion: Option[String] = None)(implicit request: DataRequest[_]): Future[Option[Result]] = {

    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

    val isInvalidDate: Boolean = startDate.isBefore(aftConnector.aftOverviewStartDate) || startDate.isAfter(DateHelper.today)

    retrieveSuspendedFlagAndSchemeStatus(ua) {
      case _ if isInvalidDate =>
        //todo redirect to new error page for invalid dates once it is created
        Future.successful(Option(Redirect(SessionExpiredController.onPageLoad())))
      case (_, schemeStatus) if !validStatuses.contains(schemeStatus) =>
        errorHandler.onClientError(request, NOT_FOUND, message = "Scheme Status Check Failed for status " + schemeStatus.toString).map(Option(_))
      case (isSuspended, _) =>
        pensionsSchemeConnector.checkForAssociation(request.psaId.id, srn)(hc, implicitly, request).flatMap {
          case true => associatedPsaRedirection(srn, startDate, isSuspended, optPage, optVersion)
          case _ => errorHandler.onClientError(request, NOT_FOUND).map(Option(_))
        }
    }
  }

  private def associatedPsaRedirection(srn: String,
                                       startDate: String,
                                       isSuspended: Boolean,
                                       optPage: Option[Page],
                                       optVersion: Option[String])
                                      (implicit request: DataRequest[_]): Future[Option[Result]] =
    (isSuspended, request.isViewOnly, optPage, optVersion, isPreviousPageWithinAFT) match {
    case (true, _, Some(AFTSummaryPage), Some(_), false) =>
      Future.successful(Option(Redirect(CannotChangeAFTReturnController.onPageLoad(srn, startDate, optVersion))))
    case (true, _, Some(ChargeTypePage), _, _) =>
      Future.successful(Option(Redirect(CannotStartAFTReturnController.onPageLoad(srn, startDate))))
    case (false, true, Some(ChargeTypePage), _, _) =>
      Future.successful(Option(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, None))))
    case (false, true, None, _, _) =>
      //todo redirect to new error page for form-pages in view-only returns once it is created
      Future.successful(Option(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, None))))
    case _ =>
      Future.successful(None)
  }

  def allowSubmission(ua: UserAnswers)(implicit request: DataRequest[_]): Future[Option[Result]] =
    ua.get(QuarterPage) match {
      case Some(quarter) if !aftService.isSubmissionDisabled(quarter.endDate) =>
        Future.successful(None)
      case _ =>
        errorHandler.onClientError(request, NOT_FOUND).map(Some.apply)
    }
}
