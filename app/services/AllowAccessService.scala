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

import com.google.inject.Inject
import connectors.SchemeDetailsConnector
import controllers.routes._
import handlers.ErrorHandler
import models.SchemeStatus.{Deregistered, Open, WoundUp}
import models.UserAnswers
import models.requests.OptionalDataRequest
import pages._
import play.api.http.Status.NOT_FOUND
import play.api.mvc.{Result, Results}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

class AllowAccessService @Inject()(pensionsSchemeConnector: SchemeDetailsConnector,
                                   aftService: AFTService,
                                   errorHandler: ErrorHandler)
                                  (implicit val executionContext: ExecutionContext) extends Results {

  private val validStatus = Seq(Open, WoundUp, Deregistered)

  def filterForIllegalPageAccess(srn: String, ua: UserAnswers, optionPage: Option[Page] = None, optionVersion: Option[String] = None)
                                (implicit request: OptionalDataRequest[_]): Future[Option[Result]] = {

    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

    (ua.get(IsPsaSuspendedQuery), ua.get(SchemeStatusQuery)) match {
      case retrievedItems if retrievedItems._1.isEmpty | retrievedItems._2.isEmpty =>
        Future.successful(Some(Redirect(controllers.routes.SessionExpiredController.onPageLoad())))
      case (_, Some(status)) if !validStatus.contains(status) =>
        errorHandler.onClientError(request, NOT_FOUND, message = "Scheme Status Check Failed for status " + status.toString).map(Option(_))
      case (Some(isSuspended), _) =>
        pensionsSchemeConnector.checkForAssociation(request.psaId.id, srn)(hc, implicitly, request).flatMap {
          case true =>
            (isSuspended, request.viewOnly, optionPage, optionVersion) match {
              case (true, false, Some(AFTSummaryPage), Some(_)) =>
                Future.successful(Option(Redirect(CannotChangeAFTReturnController.onPageLoad(srn, optionVersion))))
              case (true, false, Some(ChargeTypePage), _) =>
                Future.successful(Option(Redirect(CannotStartAFTReturnController.onPageLoad(srn))))
              case _ =>
                Future.successful(None)
            }
          case _ =>
            errorHandler.onClientError(request, NOT_FOUND).map(Option(_))
        }
    }
  }

  def allowSubmission(ua: UserAnswers)(implicit request: OptionalDataRequest[_]): Future[Option[Result]] =
    ua.get(QuarterPage) match {
      case Some(quarter) if !aftService.isSubmissionDisabled(quarter.endDate) =>
        Future.successful(None)
      case _ =>
        errorHandler.onClientError(request, NOT_FOUND, "").map(Some.apply)
    }
}
