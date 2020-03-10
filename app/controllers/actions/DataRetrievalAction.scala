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
import connectors.cache.UserAnswersCacheConnector
import javax.inject.Inject
import models.UserAnswers
import models.requests.IdentifierRequest
import models.requests.OptionalDataRequest
import pages.IsPsaSuspendedQuery
import play.api.libs.json.JsObject
import play.api.mvc.ActionTransformer
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class DataRetrievalImpl(
    srn: String,
    startDate: LocalDate,
    val userAnswersCacheConnector: UserAnswersCacheConnector
)(implicit val executionContext: ExecutionContext)
    extends DataRetrieval {

  override protected def transform[A](request: IdentifierRequest[A]): Future[OptionalDataRequest[A]] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

    val id = s"$srn$startDate"
    for {
      data <- userAnswersCacheConnector.fetch(id)
      isLocked <- userAnswersCacheConnector.isLocked(id)
    } yield {
      data match {
        case None =>
          OptionalDataRequest(request.request, id, request.psaId, None, isLocked)
        case Some(uaJsValue) =>
          val ua = UserAnswers(uaJsValue.as[JsObject])
          OptionalDataRequest(request.request, id, request.psaId, Some(ua), isLocked || ua.get(IsPsaSuspendedQuery).getOrElse(true))
      }
    }
  }
}

class DataRetrievalActionImpl @Inject()(
    userAnswersCacheConnector: UserAnswersCacheConnector
)(implicit val executionContext: ExecutionContext)
    extends DataRetrievalAction {
  override def apply(srn: String, startDate: LocalDate): DataRetrieval = new DataRetrievalImpl(srn, startDate, userAnswersCacheConnector)
}

@ImplementedBy(classOf[DataRetrievalImpl])
trait DataRetrieval extends ActionTransformer[IdentifierRequest, OptionalDataRequest]

@ImplementedBy(classOf[DataRetrievalActionImpl])
trait DataRetrievalAction {
  def apply(srn: String, startDate: LocalDate): DataRetrieval
}
