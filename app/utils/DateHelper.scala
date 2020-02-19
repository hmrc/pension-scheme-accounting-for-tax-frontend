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

package utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference

object DateHelper {

  private val mockDate: AtomicReference[Option[LocalDate]] = new AtomicReference(None)

  def today: LocalDate = mockDate.get().getOrElse(LocalDate.now())
  def setDate(date: Option[LocalDate]): Unit = mockDate.set(date)
  def overriddenDate: Option[LocalDate] = mockDate.get()

  val dateFormatterYMD: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  val dateFormatterDMY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")


}
