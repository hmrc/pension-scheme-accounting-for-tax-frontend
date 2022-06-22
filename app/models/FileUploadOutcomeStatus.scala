/*
 * Copyright 2022 HM Revenue & Customs
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

sealed trait FileUploadOutcomeStatus

object FileUploadOutcomeStatus extends Enumerable.Implicits {

  case object Success extends WithName("Success") with FileUploadOutcomeStatus

  case object ValidationErrorsLessThanMax extends WithName("ValidationErrorsLessThanMax") with FileUploadOutcomeStatus

  case object ValidationErrorsMoreThanOrEqualToMax extends WithName("ValidationErrorsMoreThanOrEqualToMax") with FileUploadOutcomeStatus

  case object GeneralError extends WithName("GeneralError") with FileUploadOutcomeStatus

  case object UpscanUnknownError extends WithName("UpscanUnknownError") with FileUploadOutcomeStatus

  case object UpscanInvalidHeaderOrBody extends WithName("UpscanInvalidHeaderOrBody") with FileUploadOutcomeStatus

  case object SessionExpired extends WithName("SessionExpired") with FileUploadOutcomeStatus


  val values: Seq[FileUploadOutcomeStatus] = Seq(
    Success,
    ValidationErrorsLessThanMax,
    ValidationErrorsMoreThanOrEqualToMax,
    GeneralError,
    UpscanUnknownError,
    UpscanInvalidHeaderOrBody,
    SessionExpired
  )

  implicit val enumerable: Enumerable[FileUploadOutcomeStatus] =
    Enumerable(values.map(v => v.toString -> v): _*)
}
