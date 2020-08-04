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

package models

import play.api.data.Form
import play.api.i18n.Messages
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.viewmodels.Radios
import uk.gov.hmrc.viewmodels.Radios.Radio
import uk.gov.hmrc.viewmodels.Text.Literal

case class SchemeDetail(name: String, referenceNumber: String, schemeStatus: String, openDate: Option[String],
                        pstr: Option[String] = None, relationShip: Option[String], underAppeal: Option[String] = None)

object SchemeDetail {
  implicit val format: Format[SchemeDetail] = Json.format[SchemeDetail]
}

case class ListOfSchemes(processingDate: String, totalSchemesRegistered: String,
                         schemeDetail: Option[List[SchemeDetail]] = None)

object ListOfSchemes {
  implicit val format: Format[ListOfSchemes] = Json.format[ListOfSchemes]
}
