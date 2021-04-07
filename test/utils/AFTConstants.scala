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

package utils

import java.time.LocalDate

object AFTConstants {
  val QUARTER_START_DATE: LocalDate = LocalDate.of(2020, 4, 1)
  val QUARTER_END_DATE: LocalDate = LocalDate.of(2020, 6, 30)
  val MIN_DATE: LocalDate = LocalDate.of(1900, 1, 1)
  val Q3_2020_START: LocalDate = LocalDate.of(2020, 7, 1)
  val Q3_2020_END: LocalDate = LocalDate.of(2020, 9, 30)
  val Q3_2021_START: LocalDate = LocalDate.of(2021, 7, 1)
  val Q3_2021_END: LocalDate = LocalDate.of(2021, 9, 30)
}
