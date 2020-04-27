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

import data.SampleData
import models.{SessionData, UserAnswers}
import models.requests.{IdentifierRequest, OptionalDataRequest}

import scala.concurrent.{ExecutionContext, Future}
import SampleData._

class FakeDataRetrievalAction(json: Option[UserAnswers],
                              sessionData: SessionData = FakeDataRetrievalAction.defaultSessionData
                             ) extends DataRetrievalAction {
  override def apply(srn: String, startDate: LocalDate): DataRetrieval = new FakeDataRetrieval(json, sessionData)
}

object FakeDataRetrievalAction {
  val defaultSessionData = sessionData(
    sessionAccessData = sessionAccessData(
      accessMode = accessModeViewOnly
    )
  )
}

class FakeDataRetrieval(dataToReturn: Option[UserAnswers], sessionData: SessionData) extends DataRetrieval {

  override protected def transform[A](request: IdentifierRequest[A]): Future[OptionalDataRequest[A]] =
    Future(OptionalDataRequest(request.request, s"srn-startDt-id", request.psaId, dataToReturn, Some(sessionData)))

  override protected implicit val executionContext: ExecutionContext =
    scala.concurrent.ExecutionContext.Implicits.global
}

