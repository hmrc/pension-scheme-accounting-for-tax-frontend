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
import play.api.mvc.{Request, WrappedRequest}
import uk.gov.hmrc.domain.{PsaId, PspId}

case class IdentifierRequest[A](
                                 request: Request[A],
                                 psaId: Option[PsaId],
                                 pspId: Option[PspId] = None
                               )
  extends WrappedRequest[A](request) {

  def idOrException: String =
    if (psaId.nonEmpty) psaId.get.id
    else if (pspId.nonEmpty) pspId.get.id
    else throw IdNotFound()

  def psaIdOrException: PsaId =
    if (psaId.nonEmpty) psaId.get
    else throw IdNotFound()

  def pspIdOrException: PspId =
    if (pspId.nonEmpty) pspId.get
    else throw IdNotFound()
}
