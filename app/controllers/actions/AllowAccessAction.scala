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

package controllers.actions

import java.time.LocalDate
import com.google.inject.{Inject, ImplementedBy}
import config.FrontendAppConfig
import connectors.{SchemeDetailsConnector, MinimalConnector, AFTConnector}
import handlers.ErrorHandler
import models.LocalDateBinder._
import models.SchemeAdministratorType.SchemeAdministratorTypePSA
import models.SchemeStatus.{WoundUp, Deregistered, Open}
import models.requests.{IdentifierRequest, DataRequest}
import models.{SchemeAdministratorType, MinimalFlags, AccessType}
import pages.{MinimalFlagsQuery, _}
import play.api.http.Status.NOT_FOUND
import play.api.mvc.Results._
import play.api.mvc.{ActionFilter, Result}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import utils.DateHelper

import scala.concurrent.{ExecutionContext, Future}

trait AllowAccessCommon {
  def minimalFlagsRedirect(minimalFlags: MinimalFlags,
    frontendAppConfig: FrontendAppConfig,
    schemeAdministratorType: SchemeAdministratorType):Option[Result] = {
    minimalFlags match {
      case MinimalFlags(true, _) => Some(Redirect(frontendAppConfig.youMustContactHMRCUrl))
      case MinimalFlags(_, true) =>
        Some(Redirect(
          if (schemeAdministratorType == SchemeAdministratorTypePSA) {
            frontendAppConfig.psaUpdateContactDetailsUrl
          } else {
            frontendAppConfig.pspUpdateContactDetailsUrl
          }
        ))
      case _ => None
    }
  }
}

class AllowAccessAction(
                         srn: String,
                         startDate: LocalDate,
                         optPage: Option[Page],
                         version: Int,
                         accessType: AccessType,
                         aftConnector: AFTConnector,
                         errorHandler: ErrorHandler,
                         frontendAppConfig: FrontendAppConfig,
                         schemeDetailsConnector: SchemeDetailsConnector
                       )(
                         implicit val executionContext: ExecutionContext
                       )
  extends ActionFilter[DataRequest] with AllowAccessCommon {
  override protected def filter[A](request: DataRequest[A]): Future[Option[Result]] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))
    val isInvalidDate: Boolean = startDate.isBefore(aftConnector.aftOverviewStartDate) || startDate.isAfter(DateHelper.today)

    (isInvalidDate, request.userAnswers.get(SchemeStatusQuery), minimalFlagChecks(request)) match {
      case (_, _, optionRedirectUrl@Some(_)) => Future.successful(optionRedirectUrl)
      case (false, Some(schemeStatus), _) =>
        if (!validStatuses.contains(schemeStatus)) {
          errorHandler.onClientError(request, NOT_FOUND, message = "Scheme Status Check Failed for status " + schemeStatus.toString).map(Option(_))
        } else {
          schemeDetailsConnector.checkForAssociation(request.idOrException, srn, getIdType(request))(hc, implicitly).flatMap {
            case true =>
              associatedPsaRedirection(srn, startDate, optPage, version, accessType)(request)
            case _ =>
              errorHandler.onClientError(request, NOT_FOUND).map(Option(_))
          }
        }
      //todo redirect to new error page for invalid dates once it is created
      case _ => Future.successful(Some(Redirect(controllers.routes.SessionExpiredController.onPageLoad())))
    }
  }

  private def minimalFlagChecks[A](request:DataRequest[A]):Option[Result] = {
    request.userAnswers.get(MinimalFlagsQuery) match {
      case None => Some(Redirect(
        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version)))
      case Some(mf) =>
        minimalFlagsRedirect(mf, frontendAppConfig, request.schemeAdministratorType)
    }
  }

  private def getIdType[A](request: DataRequest[A]):String = {
    (request.psaId, request.pspId) match {
      case (Some(_), _) => "psaId"
      case (_, Some(_)) => "pspId"
      case _ => throw new Exception("Unable to get ID from request")
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

class AllowAccessActionForIdentifierRequest(
                         frontendAppConfig: FrontendAppConfig,
                         minimalConnector: MinimalConnector
                       )(
                         implicit val executionContext: ExecutionContext
                       )
  extends ActionFilter[IdentifierRequest] with AllowAccessCommon {
  override protected def filter[A](request: IdentifierRequest[A]): Future[Option[Result]] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))
    minimalConnector.getMinimalDetails(implicitly, implicitly, request).map { minimalDetails =>
      minimalFlagsRedirect(MinimalFlags(minimalDetails.deceasedFlag, minimalDetails.rlsFlag),
        frontendAppConfig, request.schemeAdministratorType) match {
        case optionRedirectUrl@Some(_) => optionRedirectUrl
        case _ => None
      }
    }
  }
}

@ImplementedBy(classOf[AllowAccessActionProviderImpl])
trait AllowAccessActionProvider {
  def apply(srn: String, startDate: LocalDate, optionPage: Option[Page] = None, version: Int, accessType: AccessType): ActionFilter[DataRequest]
}

class AllowAccessActionProviderImpl @Inject()(aftConnector: AFTConnector,
                                              errorHandler: ErrorHandler,
                                              frontendAppConfig: FrontendAppConfig,
                                              schemeDetailsConnector: SchemeDetailsConnector)(implicit ec: ExecutionContext) extends AllowAccessActionProvider {
  def apply(srn: String, startDate: LocalDate, optionPage: Option[Page] = None, version: Int, accessType: AccessType) =
    new AllowAccessAction(srn, startDate, optionPage, version, accessType, aftConnector, errorHandler, frontendAppConfig, schemeDetailsConnector)
}

@ImplementedBy(classOf[AllowAccessActionProviderForIdentifierRequestImpl])
trait AllowAccessActionProviderForIdentifierRequest {
  def apply(): ActionFilter[IdentifierRequest]
}

class AllowAccessActionProviderForIdentifierRequestImpl @Inject()(
  frontendAppConfig: FrontendAppConfig,
  minimalConnector: MinimalConnector
)(implicit ec: ExecutionContext) extends AllowAccessActionProviderForIdentifierRequest {
  def apply() = new AllowAccessActionForIdentifierRequest(frontendAppConfig, minimalConnector)
}
