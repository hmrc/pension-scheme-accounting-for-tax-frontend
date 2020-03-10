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

package connectors

import com.google.inject.Inject
import config.FrontendAppConfig
import models.SchemeDetails
import play.api.http.Status
import play.api.libs.json.JsError
import play.api.libs.json.JsResultException
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json
import play.api.mvc.RequestHeader
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class SchemeDetailsConnector @Inject()(http: HttpClient, config: FrontendAppConfig) {

  def getSchemeDetails(psaId: String, schemeIdType: String, idNumber: String)(implicit hc: HeaderCarrier,
                                                                              ec: ExecutionContext): Future[SchemeDetails] = {

    val url = config.schemeDetailsUrl
    val schemeHc = hc.withExtraHeaders(headers = "schemeIdType" -> schemeIdType, "idNumber" -> idNumber, "PSAId" -> psaId)
    http.GET[SchemeDetails](url)(implicitly, schemeHc, implicitly)
  }

  def checkForAssociation(psaId: String, srn: String)(implicit
                                                      headerCarrier: HeaderCarrier,
                                                      ec: ExecutionContext,
                                                      request: RequestHeader): Future[Boolean] = {
    val headers: Seq[(String, String)] = Seq(("psaId", psaId), ("schemeReferenceNumber", srn), ("Content-Type", "application/json"))

    implicit val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers: _*)

    http.GET[HttpResponse](config.checkAssociationUrl)(implicitly, hc, implicitly).map { response =>
      require(response.status == Status.OK)

      val json = Json.parse(response.body)

      json.validate[Boolean] match {
        case JsSuccess(value, _) => value
        case JsError(errors)     => throw JsResultException(errors)
      }
    }
  }
}
