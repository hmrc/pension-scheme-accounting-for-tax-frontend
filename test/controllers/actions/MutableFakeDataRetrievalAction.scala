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

import models.AccessMode
import models.SessionAccessData
import models.SessionData
import models.UserAnswers
import models.requests.IdentifierRequest
import models.requests.OptionalDataRequest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class MutableFakeDataRetrievalAction extends DataRetrievalAction {
  private var dataToReturn: Option[UserAnswers] = None
  private var storedSessionData: SessionData = SessionData(
    sessionId = "1",
    lockDetail = None,
    sessionAccessData = SessionAccessData(
      version = 1,
      accessMode = AccessMode.PageAccessModeCompile,
      areSubmittedVersionsAvailable = false
    )
  )

  def setDataToReturn(userAnswers: Option[UserAnswers]): Unit = dataToReturn = userAnswers
  def setSessionData(sessionData: SessionData): Unit = storedSessionData = sessionData
  def setViewOnly(viewOnly: Boolean): Unit = {
    storedSessionData = storedSessionData copy (
      sessionAccessData = SessionAccessData(
        storedSessionData.sessionAccessData.version,
        if (viewOnly) AccessMode.PageAccessModeCompile else AccessMode.PageAccessModeViewOnly,
        areSubmittedVersionsAvailable = false
      )
    )
  }

  override def apply(srn: String, startDate: LocalDate): DataRetrieval = new MutableFakeDataRetrieval(storedSessionData, dataToReturn)
}

class MutableFakeDataRetrieval(sessionData: SessionData = MutableFakeDataRetrieval.sessionDataViewOnly, dataToReturn: Option[UserAnswers])
    extends DataRetrieval {

  override protected def transform[A](request: IdentifierRequest[A]): Future[OptionalDataRequest[A]] = {
    Future(OptionalDataRequest(request.request, s"srn-startDt-id", request.psaId, request.pspId, dataToReturn, Some(sessionData)))
  }

  override protected implicit val executionContext: ExecutionContext =
    scala.concurrent.ExecutionContext.Implicits.global
}

object MutableFakeDataRetrieval {
  private val sessionDataViewOnly: SessionData = SessionData(
    sessionId = "1",
    lockDetail = None,
    sessionAccessData = SessionAccessData(
      version = 1,
      accessMode = AccessMode.PageAccessModeViewOnly,
      areSubmittedVersionsAvailable = false
    )
  )
}
