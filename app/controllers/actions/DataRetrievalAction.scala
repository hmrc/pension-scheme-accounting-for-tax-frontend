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
import models.{AccessType, UserAnswers}
import models.requests.IdentifierRequest
import models.requests.OptionalDataRequest
import pages.Page
import play.api.libs.json.JsObject
import play.api.mvc.{ActionTransformer, Request}
import services.RequestCreationService
import uk.gov.hmrc.domain.PsaId
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class DataRetrievalImpl(
    srn: String,
    startDate: LocalDate,
    userAnswersCacheConnector: UserAnswersCacheConnector
)(implicit val executionContext: ExecutionContext)
    extends DataRetrieval {
  
  override protected def transform[A](request: IdentifierRequest[A]): Future[OptionalDataRequest[A]] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))
    val id = s"$srn$startDate"
    for {
      data <- userAnswersCacheConnector.fetch(id)
      sessionData <- userAnswersCacheConnector.getSessionData(id)
    } yield {
      val optionUA = data.map(jsValue => UserAnswers(jsValue.as[JsObject]))
      OptionalDataRequest[A](request, id, request.psaId, optionUA, sessionData)
    }
  }


}

class DataRetrievalActionImpl @Inject()(userAnswersCacheConnector: UserAnswersCacheConnector
                                        )(implicit val executionContext: ExecutionContext)
                                        extends DataRetrievalAction {
  override def apply(srn: String, startDate: LocalDate): DataRetrieval =
    new DataRetrievalImpl(srn, startDate, userAnswersCacheConnector)
}

@ImplementedBy(classOf[DataRetrievalImpl])
trait DataRetrieval extends ActionTransformer[IdentifierRequest, OptionalDataRequest]

@ImplementedBy(classOf[DataRetrievalActionImpl])
trait DataRetrievalAction {
  def apply(srn: String, startDate: LocalDate): DataRetrieval
}
