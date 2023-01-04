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

import play.api.Logger


object TimeLogger {

  private val logger = Logger("FileUploadLogger")

  def logOperationTime[T](f: => T, description: String): T = {
    val start = System.currentTimeMillis
    val call = f
    val totalTime = System.currentTimeMillis() - start
    logger.warn(s"FileUpload logging time $description is $totalTime ms")
    call
  }

}
