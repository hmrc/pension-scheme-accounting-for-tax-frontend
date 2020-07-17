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

package controllers.actions

import java.time.LocalDate

import com.google.inject.ImplementedBy
import com.google.inject.Inject
import connectors.AFTConnector
import connectors.SchemeDetailsConnector
import controllers.routes.SessionExpiredController
import handlers.ErrorHandler
import models.LocalDateBinder._
import models.SchemeStatus.Deregistered
import models.SchemeStatus.Open
import models.SchemeStatus.WoundUp
import models.requests.DataRequest
import models.AccessType
import pages._
import play.api.http.Status.NOT_FOUND
import play.api.mvc.Results._
import play.api.mvc.ActionFilter
import play.api.mvc.Result
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import utils.DateHelper

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class AllowAccessAction(srn: String,
                        startDate: LocalDate,
                        optPage:Option[Page],
                        version: Int,
                        accessType: AccessType,
                        aftConnector: AFTConnector,
                        errorHandler: ErrorHandler,
                        schemeDetailsConnector: SchemeDetailsConnector)
                       (implicit val executionContext: ExecutionContext)
    extends ActionFilter[DataRequest] {
  override protected def filter[A](request: DataRequest[A]): Future[Option[Result]] = {

    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

    val isInvalidDate: Boolean = startDate.isBefore(aftConnector.aftOverviewStartDate) || startDate.isAfter(DateHelper.today)

    if (isInvalidDate) {
      //todo redirect to new error page for invalid dates once it is created
      Future.successful(Option(Redirect(SessionExpiredController.onPageLoad())))
    } else {
      request.userAnswers.get(SchemeStatusQuery) match {
        case Some(schemeStatus) =>
          if (!validStatuses.contains(schemeStatus)) {
            errorHandler.onClientError(request, NOT_FOUND, message = "Scheme Status Check Failed for status " + schemeStatus.toString).map(Option(_))
          } else {
            schemeDetailsConnector.checkForAssociation(request.psaId.id, srn)(hc, implicitly, request).flatMap {
              case true => associatedPsaRedirection(srn, startDate, optPage, version, accessType)(request)
              case _ => errorHandler.onClientError(request, NOT_FOUND).map(Option(_))
            }
          }
        case _ => Future.successful(Some(Redirect(controllers.routes.SessionExpiredController.onPageLoad())))
      }
    }
  }

  private val validStatuses = Seq(Open, WoundUp, Deregistered)

  private def associatedPsaRedirection(srn: String,
                                       startDate: String,
                                       optPage: Option[Page],
                                       version: Int,
                                       accessType: AccessType)
                                      (implicit request: DataRequest[_]): Future[Option[Result]] = {
    (request.isViewOnly, optPage) match {
      case (true, None | Some(ChargeTypePage)) =>
        //todo redirect to new error page for form-pages in view-only returns once it is created
        Future.successful(Option(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version))))
      case _ =>
        Future.successful(None)
    }
  }
}

@ImplementedBy(classOf[AllowAccessActionProviderImpl])
trait AllowAccessActionProvider {
  def apply(srn: String, startDate: LocalDate, optionPage:Option[Page] = None, version: Int, accessType: AccessType): ActionFilter[DataRequest]
}

class AllowAccessActionProviderImpl @Inject()(aftConnector: AFTConnector,
                                              errorHandler: ErrorHandler,
                                              schemeDetailsConnector: SchemeDetailsConnector)(implicit ec: ExecutionContext) extends AllowAccessActionProvider {
  def apply(srn: String, startDate: LocalDate, optionPage:Option[Page] = None, version: Int, accessType: AccessType) =
    new AllowAccessAction(srn, startDate, optionPage, version, accessType, aftConnector, errorHandler, schemeDetailsConnector)
}
