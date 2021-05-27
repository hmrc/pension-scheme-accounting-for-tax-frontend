/*
 * Copyright 2021 HM Revenue & Customs
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

package connectors.cache

import uk.gov.hmrc.http.HeaderCarrier

object CacheConnectorHeaders {
  val names: HeaderCarrier => Seq[String] = hc =>
    Seq(hc.names.authorisation, hc.names.xRequestId, hc.names.xSessionId)

  def headers(hc: HeaderCarrier, extraHeaders: Seq[(String, String)] = Seq.empty): Seq[(String, String)] = {
    hc.headers(names(hc)) ++ hc.withExtraHeaders(
      Seq(("content-type", "application/json")) ++ extraHeaders: _*
    ).extraHeaders
  }
}
