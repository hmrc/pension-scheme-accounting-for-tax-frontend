/*
 * Copyright 2023 HM Revenue & Customs
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

package fileUploadParsers

trait McCloudParser  {
  protected def countNoOfSchemes(columns: Seq[String], startFrom: Int): Int = {
    val default: Int => String = _ => ""
    val processedSeq = ( startFrom until columns.size by 3).takeWhile { w =>
      columns.applyOrElse(w, default).nonEmpty ||
        columns.applyOrElse(w + 1, default).nonEmpty ||
        columns.applyOrElse(w + 2, default).nonEmpty
    }
    processedSeq.size
  }
}
