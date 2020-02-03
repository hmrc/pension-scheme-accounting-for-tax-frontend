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
import connectors.SchemeDetailsConnector
import handlers.ErrorHandler
import models.requests.OptionalDataRequest
import play.api.http.Status.NOT_FOUND
import play.api.mvc.{ActionFilter, Result}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

class AllowAccessAction (
                        srn:String,
                        pensionsSchemeConnector: SchemeDetailsConnector,
                        errorHandler: ErrorHandler
                       )(implicit val executionContext: ExecutionContext) extends ActionFilter[OptionalDataRequest] {

  override protected def filter[A](request: OptionalDataRequest[A]): Future[Option[Result]] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))
    checkForAssociation(request, srn)
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
  def apply(srn:String): ActionFilter[OptionalDataRequest]
}

class AllowAccessActionProviderImpl @Inject()(
                                           pensionsSchemeConnector: SchemeDetailsConnector,
                                           errorHandler: ErrorHandler
                                         )(implicit ec: ExecutionContext) extends AllowAccessActionProvider {
  def apply(srn:String) = new AllowAccessAction(srn, pensionsSchemeConnector, errorHandler)
}
