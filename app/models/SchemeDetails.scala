/*
 * Copyright 2025 HM Revenue & Customs
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

package models

import play.api.libs.functional.syntax._
import play.api.libs.json._


case class SchemeDetails(
  schemeName: String,
  pstr: String,
  schemeStatus: String,
  authorisingPSAID: Option[String]
)

object SchemeDetails {

  implicit val readsPsa: Reads[SchemeDetails] =
    (
      (JsPath \ "schemeName").read[String] and
        (JsPath \ "pstr").read[String] and
        (JsPath \ "schemeStatus").read[String]
      )((schemeName, pstr, status) => SchemeDetails(schemeName, pstr, status, None))

  implicit val readsPsp: Reads[SchemeDetails] =
    (
      (JsPath \ "schemeName").read[String] and
        (JsPath \ "pstr").read[String] and
        (JsPath \ "schemeStatus").read[String] and
        (JsPath \ "pspDetails" \ "authorisingPSAID").read[String]
      )((schemeName, pstr, status, authorisingPSAID) => SchemeDetails(schemeName, pstr, status, Some(authorisingPSAID)))

  implicit val reads: Reads[SchemeDetails] = Reads[SchemeDetails] { json =>
    (json \ "pspDetails").validate[JsObject] match {
      case JsSuccess(_, _) => readsPsp.reads(json) // Use PSP Reads if `pspDetails` exists
      case JsError(_)      => readsPsa.reads(json) // Otherwise, fallback to PSA Reads
    }
  }

  implicit val writes: OWrites[SchemeDetails] = Json.writes[SchemeDetails]

  implicit val format: OFormat[SchemeDetails] = OFormat(reads, writes)
}
