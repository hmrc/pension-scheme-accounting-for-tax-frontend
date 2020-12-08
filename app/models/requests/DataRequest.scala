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

package models.requests

import controllers.actions.IdNotFound
import models.AccessMode
import models.SessionData
import play.api.mvc.{Request, WrappedRequest}
import models.UserAnswers
import uk.gov.hmrc.domain.{PsaId, PspId}

case class OptionalDataRequest[A] (
                                    request: Request[A],
                                    internalId: String,
                                    psaId: Option[PsaId],
                                    pspId: Option[PspId],
                                    userAnswers: Option[UserAnswers],
                                    sessionData: Option[SessionData]
                                  ) extends WrappedRequest[A](request)

case class DataRequest[A] (
                            request: Request[A],
                            internalId: String,
                            psaId: Option[PsaId],
                            pspId: Option[PspId],
                            userAnswers: UserAnswers,
                            sessionData: SessionData
                          ) extends WrappedRequest[A](request) {
  def aftVersion: Int = sessionData.sessionAccessData.version
  def areSubmittedVersionsAvailable: Boolean = sessionData.sessionAccessData.areSubmittedVersionsAvailable
  def isAmendment: Boolean = aftVersion > 1
  def isViewOnly: Boolean = sessionData.sessionAccessData.accessMode == AccessMode.PageAccessModeViewOnly
  def isPrecompile: Boolean = sessionData.sessionAccessData.accessMode == AccessMode.PageAccessModePreCompile
  def isEditable: Boolean = !isViewOnly
  def isLocked: Boolean = sessionData.lockDetail.isDefined
  def isLockedByPsp: Boolean = sessionData.lockDetail
    .exists(ld => ld.psaOrPspId.nonEmpty && ld.psaOrPspId.charAt(0).isDigit)
  def isLockedByPsa: Boolean = sessionData.lockDetail
    .exists(ld => ld.psaOrPspId.nonEmpty && ld.psaOrPspId.charAt(0).isLetter)
  def idOrException: String =
    (psaId, pspId) match {
      case (Some(id), _) => id.id
      case (_, Some(id)) => id.id
      case _ => throw IdNotFound()
    }
  def submitterType:String = (psaId, pspId) match {
    case (Some(_), None) => "PSA"
    case (None, Some(_)) => "PSP"
    case _ => throw IdNotFound()
  }
}
