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

package models.viewModels.paymentsAndCharges
import play.api.i18n.Messages
import play.api.libs.functional.syntax._
import play.api.libs.json._
import viewmodels.Table

case class PaymentsAndChargesTable(caption: String, table: Table)

object PaymentsAndChargesTable {
  implicit def writes(implicit messages: Messages): Writes[PaymentsAndChargesTable] =
    ((JsPath \ "caption").write[String] and
      (JsPath \ "table").write[Table](Table.writes))(sd => (sd.caption, sd.table))
}
