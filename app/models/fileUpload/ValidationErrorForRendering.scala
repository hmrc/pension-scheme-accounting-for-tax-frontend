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

package models.fileUpload

import org.apache.commons.lang3.StringUtils.EMPTY
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{JsPath, Reads}
case class ValidationErrorForRendering(cell: String, error: String, columnName: String = EMPTY)

object ValidationErrorForRendering {
  private val readsErrorDetails: Reads[ValidationErrorForRendering] = (
    (JsPath \ "cell").read[String] and
      (JsPath \ "error").read[String]
    )((cell, error) =>
    ValidationErrorForRendering(
      cell,
      error
    )
  )

  implicit val reads: Reads[Seq[ValidationErrorForRendering]] = (JsPath \ "errors").read[Seq[ValidationErrorForRendering]](Reads.seq(readsErrorDetails))
}
