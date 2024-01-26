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

package models.requests

import controllers.actions.IdNotFound
import models.AdministratorOrPractitioner
import models.AdministratorOrPractitioner.{Administrator, Practitioner}
import play.api.mvc.{Request, WrappedRequest}
import uk.gov.hmrc.domain.{PsaId, PspId}

case class IdentifierRequest[A](
                                 externalId: String,
                                 request: Request[A],
                                 psaId: Option[PsaId],
                                 pspId: Option[PspId] = None
                               )
  extends WrappedRequest[A](request) {

  def idOrException: String =
    (psaId, pspId) match {
      case (Some(id), _) => id.id
      case (_, Some(id)) => id.id
      case _ => throw IdNotFound()
    }

  def psaIdOrException: PsaId =
    psaId match {
      case Some(id) => id
      case _ => throw IdNotFound()
    }

  def pspIdOrException: PspId =
    pspId match {
      case Some(id) => id
      case _ => throw IdNotFound()
    }

  def schemeAdministratorType:AdministratorOrPractitioner = (psaId, pspId) match {
    case (Some(_), None) => Administrator
    case (None, Some(_)) => Practitioner
    case _ => throw IdNotFound()
  }
}
