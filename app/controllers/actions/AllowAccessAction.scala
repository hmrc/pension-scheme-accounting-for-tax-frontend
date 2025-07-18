/*
 * Copyright 2024 HM Revenue & Customs
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

import com.google.inject.{ImplementedBy, Inject}
import config.FrontendAppConfig
import connectors.{AFTConnector, DelimitedAdminException, MinimalConnector, SchemeDetailsConnector}
import handlers.ErrorHandler
import models.AdministratorOrPractitioner.Administrator
import models.LocalDateBinder._
import models.SchemeStatus.{Deregistered, Open, WoundUp}
import models.requests.{DataRequest, IdentifierRequest}
import models.{AccessType, AdministratorOrPractitioner, MinimalFlags}
import pages._
import play.api.Logging
import play.api.http.Status.NOT_FOUND
import play.api.mvc.Results._
import play.api.mvc.{ActionFilter, Call, Result}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import utils.DateHelper

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

trait AllowAccessCommon {
  protected def minimalFlagsRedirect(minimalFlags: MinimalFlags,
    frontendAppConfig: FrontendAppConfig,
    schemeAdministratorType: AdministratorOrPractitioner):Option[Result] = {
    minimalFlags match {
      case MinimalFlags(true, _) => Some(Redirect(frontendAppConfig.youMustContactHMRCUrl))
      case MinimalFlags(_, true) =>
        Some(Redirect(
          if (schemeAdministratorType == Administrator) {
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
  extends ActionFilter[DataRequest] with AllowAccessCommon with Logging {
  override protected def filter[A](request: DataRequest[A]): Future[Option[Result]] = {
    implicit val hc: HeaderCarrier =
      HeaderCarrierConverter.fromRequestAndSession(request, request.session)
    val isInvalidDate: Boolean = startDate.isBefore(aftConnector.aftOverviewStartDate) || startDate.isAfter(DateHelper.today)

    (isInvalidDate, request.userAnswers.get(SchemeStatusQuery), minimalFlagChecks(request)) match {
      case (_, _, optionRedirectUrl@Some(_)) => Future.successful(optionRedirectUrl)
      case (false, Some(schemeStatus), _) =>
        if (!validStatuses.contains(schemeStatus)) {
          errorHandler.onClientError(request, NOT_FOUND, message = "Allow access action - Scheme Status Check Failed for status " + schemeStatus.toString).map(Option(_))
        } else {
          schemeDetailsConnector.checkForAssociation(request.idOrException, srn, getIdType(request))(hc, implicitly).flatMap {
            case true =>
              associatedPsaRedirection(srn, startDate, optPage, version, accessType)(request)
            case _ =>
              logger.warn("Potentially prevented unauthorised access")
              errorHandler.onClientError(request, NOT_FOUND, message = "Allow access action - PSA not associated: id is " + request.idOrException + " and srn is " + srn).map(Option(_))
          }
        }
      //todo redirect to new error page for invalid dates once it is created
      case _ => Future.successful(Some(Redirect(controllers.routes.SessionExpiredController.onPageLoad)))
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
                                      (implicit request: DataRequest[?]): Future[Option[Result]] = {
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
                         minimalConnector: MinimalConnector,
                         schemeDetailsConnector: SchemeDetailsConnector,
                         errorHandler: ErrorHandler,
                         srnOpt: Option[String]
                       )(
                         implicit val executionContext: ExecutionContext
                       )
  extends ActionFilter[IdentifierRequest] with AllowAccessCommon with Logging {

  private def getIdType[A](request: IdentifierRequest[A]):String = {
    (request.psaId, request.pspId) match {
      case (Some(_), _) => "psaId"
      case (_, Some(_)) => "pspId"
      case _ => throw new Exception("Unable to get ID from request")
    }
  }
  override protected def filter[A](request: IdentifierRequest[A]): Future[Option[Result]] = {
    implicit val hc: HeaderCarrier =
      HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    val accessAllowedFtr = srnOpt.map { srn =>
      schemeDetailsConnector
        .checkForAssociation(request.idOrException, srn, getIdType(request))
        .recover { err =>
          logger.error("Cannot check for association", err)
          false
        }
    }.getOrElse(Future.successful(true))

    accessAllowedFtr.flatMap {
      case false =>
        logger.warn("Potentially prevented unauthorised access")
        errorHandler.onClientError(request, NOT_FOUND).map(Some(_))
      case true => minimalConnector
        .getMinimalDetails(hc, executionContext, request)
        .map { minimalDetails =>
          minimalFlagsRedirect(
            MinimalFlags(minimalDetails.deceasedFlag, minimalDetails.rlsFlag),
            frontendAppConfig,
            request.schemeAdministratorType
          ) match {
            case optionRedirectUrl@Some(_) => optionRedirectUrl
            case _ => None
          }
        } recoverWith {
        case _: DelimitedAdminException =>
          Future.successful(Some(Redirect(Call("GET", frontendAppConfig.delimitedPsaUrl))))
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
  def apply(srn: Option[String] = None): ActionFilter[IdentifierRequest]
}

class AllowAccessActionProviderForIdentifierRequestImpl @Inject()(
  frontendAppConfig: FrontendAppConfig,
  minimalConnector: MinimalConnector,
  schemeDetailsConnector: SchemeDetailsConnector,
  errorHandler: ErrorHandler
)(implicit ec: ExecutionContext) extends AllowAccessActionProviderForIdentifierRequest {
  def apply(srn: Option[String]) =
    new AllowAccessActionForIdentifierRequest(frontendAppConfig, minimalConnector, schemeDetailsConnector, errorHandler, srn)
}
