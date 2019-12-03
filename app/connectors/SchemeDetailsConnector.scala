/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.libs.json.{JsError, JsResultException, JsSuccess, Json}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

class SchemeDetailsConnector @Inject()(http: HttpClient, config: FrontendAppConfig) {

  def getSchemeName(psaId: String,
                    schemeIdType: String,
                    idNumber: String)(implicit hc: HeaderCarrier,
                                      ec: ExecutionContext): Future[String] = {

    val url = config.schemeDetailsUrl
    val schemeHc = hc.withExtraHeaders("schemeIdType" -> schemeIdType, "idNumber" -> idNumber, "PSAId" -> psaId)
    http.GET[HttpResponse](url)(implicitly, schemeHc, implicitly).map { response =>
      val json = Json.parse(response.body)
      (json \ "schemeName").validate[String] match {
        case JsSuccess(value, _) =>
          value
        case JsError(errors) => throw new JsResultException(errors)
      }
    }
  }
}

