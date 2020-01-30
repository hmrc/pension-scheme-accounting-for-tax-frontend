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


import com.google.inject.{ImplementedBy, Inject}
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import connectors.{MinimalPsaConnector, SchemeDetailsConnector}
import handlers.ErrorHandler
import models.UserAnswers
import models.requests.OptionalDataRequest
import pages.IsSuspendedQuery
import play.api.http.Status.NOT_FOUND
import play.api.mvc.{ActionFilter, Call, Result, Results}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

class AllowAccessAction( srn: String,
                         pensionsSchemeConnector: SchemeDetailsConnector,
                         errorHandler: ErrorHandler,
                         minimalPsaConnector: MinimalPsaConnector,
                         userAnswersCacheConnector: UserAnswersCacheConnector,
                         config: FrontendAppConfig
                       )(implicit val executionContext: ExecutionContext) extends ActionFilter[OptionalDataRequest] with Results {

  override protected def filter[A](request: OptionalDataRequest[A]): Future[Option[Result]] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))
    checkForSuspended(request, srn).flatMap {
      case false => checkForAssociation(request, srn)
      case _ =>
        Future.successful(Some(Redirect(Call("GET", config.cannotMakeChangesUrl.format(srn)))))
    }
  }

  private def checkForSuspended[A](request: OptionalDataRequest[A],
                                   extractedSRN: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    val ua = request.userAnswers.getOrElse(UserAnswers())
    ua.get(IsSuspendedQuery) match {
      case None =>
        minimalPsaConnector.isPsaSuspended(request.psaId.id)
          .flatMap(retrievedIsSuspendedValue =>
            Future.fromTry(ua.set(IsSuspendedQuery, retrievedIsSuspendedValue))
              .flatMap(uaAfterSet =>
                userAnswersCacheConnector
                  .save(request.internalId, uaAfterSet.data)
                  .map(_ => retrievedIsSuspendedValue)
              )
          )
      case Some(isSuspended) => Future.successful(isSuspended)
    }
  }


  private def checkForAssociation[A](request: OptionalDataRequest[A],
                                     extractedSRN: String)(implicit hc: HeaderCarrier): Future[Option[Result]] =
    pensionsSchemeConnector.checkForAssociation(request.psaId.id, extractedSRN)(hc, implicitly, request).flatMap {
      case true => Future.successful(None)
      case _ => errorHandler.onClientError(request, NOT_FOUND, "").map(Some.apply)
    }
}

@ImplementedBy(classOf[AllowAccessActionProviderImpl])
trait AllowAccessActionProvider {
  def apply(srn: String): ActionFilter[OptionalDataRequest]
}

class AllowAccessActionProviderImpl @Inject()( pensionsSchemeConnector: SchemeDetailsConnector,
                                               errorHandler: ErrorHandler,
                                               minimalPsaConnector: MinimalPsaConnector,
                                               userAnswersCacheConnector: UserAnswersCacheConnector,
                                               config: FrontendAppConfig
                                             )(implicit ec: ExecutionContext) extends AllowAccessActionProvider {
  def apply(srn: String) = new AllowAccessAction(srn, pensionsSchemeConnector, errorHandler, minimalPsaConnector, userAnswersCacheConnector, config)
}
