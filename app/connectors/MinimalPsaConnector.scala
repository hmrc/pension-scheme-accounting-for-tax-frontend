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
import play.api.libs.json._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

class MinimalPsaConnector @Inject()(http: HttpClient, config: FrontendAppConfig) {
  import MinimalPsaConnector._

  def getMinimalPsaDetails(psaId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[MinimalPSA] = {
    val psaHc = hc.withExtraHeaders("psaId" -> psaId)

    http.GET[MinimalPSA](config.minimalPsaDetailsUrl)(implicitly, psaHc, implicitly)
  }
}
object MinimalPsaConnector {
  case class MinimalPSA(email: String, isPsaSuspended: Boolean)

  object MinimalPSA {
    implicit val format: Format[MinimalPSA] = Json.format[MinimalPSA]
  }
}
