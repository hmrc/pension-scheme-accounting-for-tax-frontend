/*
 * Copyright 2024 HM Revenue & Customs
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

import java.time.LocalDate
package object dateOrdering {

  implicit val orderingLocalDate: Ordering[LocalDate] = Ordering.by(d => (d.getYear, d.getDayOfYear))

  implicit class LocalDateOps(private val localDate: LocalDate) extends AnyVal with Ordered[LocalDate] {
    override def compare(that: LocalDate): Int = Ordering[LocalDate].compare(localDate, that)
  }
}
