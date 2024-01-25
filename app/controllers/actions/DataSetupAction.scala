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

package controllers.actions

import com.google.inject.ImplementedBy
import models.AccessType
import models.requests.{IdentifierRequest, OptionalDataRequest}
import pages.Page
import play.api.mvc.ActionTransformer
import services.RequestCreationService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}


class DataSetupImpl(
                      srn: String,
                      startDate: LocalDate,
                      version: Int,
                      accessType: AccessType,
                      optionCurrentPage: Option[Page],
                      requestCreationService:RequestCreationService
                    )(implicit val executionContext: ExecutionContext)
  extends DataSetup {

  override protected def transform[A](request: IdentifierRequest[A]): Future[OptionalDataRequest[A]] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)
    requestCreationService.retrieveAndCreateRequest(srn, startDate, version, accessType, optionCurrentPage)(request,implicitly, implicitly)
  }
}

class DataSetupActionImpl @Inject()(
                                      requestCreationService:RequestCreationService
                                    )(implicit val executionContext: ExecutionContext)
  extends DataSetupAction {
  override def apply(srn: String, startDate: LocalDate, optionVersion: Int, accessType: AccessType, optionCurrentPage: Option[Page]): DataSetup =
    new DataSetupImpl(srn, startDate, optionVersion, accessType, optionCurrentPage, requestCreationService)
}

@ImplementedBy(classOf[DataSetupImpl])
trait DataSetup extends ActionTransformer[IdentifierRequest, OptionalDataRequest]

@ImplementedBy(classOf[DataSetupActionImpl])
trait DataSetupAction {
  def apply(srn: String, startDate: LocalDate, version: Int, accessType: AccessType, optionCurrentPage: Option[Page]): DataSetup
}
