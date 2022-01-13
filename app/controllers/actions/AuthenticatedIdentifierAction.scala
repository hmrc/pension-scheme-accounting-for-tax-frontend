/*
 * Copyright 2022 HM Revenue & Customs
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

import com.google.inject.Inject
import config.FrontendAppConfig
import connectors.cache.SessionDataCacheConnector
import controllers.routes
import models.AdministratorOrPractitioner
import models.AdministratorOrPractitioner.{Administrator, Practitioner}
import models.requests.IdentifierRequest
import play.api.Logger
import play.api.mvc.Results._
import play.api.mvc._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.domain.{PsaId, PspId}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

trait IdentifierAction
  extends ActionBuilder[IdentifierRequest, AnyContent]
    with ActionFunction[Request, IdentifierRequest]

class AuthenticatedIdentifierAction @Inject()(
                                               override val authConnector: AuthConnector,
                                               config: FrontendAppConfig,
                                               sessionDataCacheConnector: SessionDataCacheConnector,
                                               val parser: BodyParsers.Default
                                             )(implicit val executionContext: ExecutionContext)
  extends IdentifierAction
    with AuthorisedFunctions {

  private val logger = Logger(classOf[AuthenticatedIdentifierAction])

  private def bothPsaAndPspEnrolmentsPresent(enrolments: Enrolments):Boolean =
    enrolments.getEnrolment("HMRC-PODS-ORG").isDefined && enrolments.getEnrolment("HMRC-PODSPP-ORG").isDefined

  override def invokeBlock[A](
                               request: Request[A],
                               block: IdentifierRequest[A] => Future[Result]
                             ): Future[Result] = {

    implicit val hc: HeaderCarrier =
      HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    authorised(Enrolment("HMRC-PODS-ORG") or Enrolment("HMRC-PODSPP-ORG")).retrieve(
      Retrievals.externalId and Retrievals.allEnrolments
    ) {
      case Some(id) ~ enrolments if bothPsaAndPspEnrolmentsPresent(enrolments) =>
        administratorOrPractitioner(id).flatMap{
          case None =>  Future.successful(Redirect(Call("GET",config.administratorOrPractitionerUrl)))
          case Some(Administrator) => block(IdentifierRequest(id, request, getPsaId(enrolments), None))
          case Some(Practitioner) => block(IdentifierRequest(id, request, None, getPspId(enrolments)))
        }
      case Some(id) ~ enrolments =>
        block(IdentifierRequest(id, request, getPsaId(enrolments), getPspId(enrolments)))
    } recover {
      case _: NoActiveSession =>
        Redirect(config.loginUrl, Map("continue" -> Seq(config.loginContinueUrl)))
      case e: AuthorisationException =>
        logger.warn(s"Authorization Failed with error $e")
        Redirect(routes.UnauthorisedController.onPageLoad)
    }
  }

  private def getPsaId(enrolments: Enrolments): Option[PsaId] =
    enrolments
      .getEnrolment(key = "HMRC-PODS-ORG")
      .flatMap(_.getIdentifier("PSAID"))
      .map(x => PsaId(x.value))

  private def getPspId(enrolments: Enrolments): Option[PspId] =
    enrolments
      .getEnrolment(key = "HMRC-PODSPP-ORG")
      .flatMap(_.getIdentifier("PSPID"))
      .map(x => PspId(x.value))


  private def administratorOrPractitioner(id:String)(implicit hc:HeaderCarrier):Future[Option[AdministratorOrPractitioner]] = {
    sessionDataCacheConnector.fetch(id).map { optionJsValue =>
      optionJsValue.flatMap { json =>
        (json \ "administratorOrPractitioner").toOption.flatMap(_.validate[AdministratorOrPractitioner].asOpt)
      }
    }
  }
}
