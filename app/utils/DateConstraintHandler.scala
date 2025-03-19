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

package utils

import forms.mappings.Mappings
import play.api.data.Mapping
import play.api.data.validation.Constraint
import play.api.i18n.Messages
import utils.DateHelper.formatDateDMY

import java.time.LocalDate

trait DateConstraintHandler[T] {
  def handle(input: T, errorKey: String)(implicit messages: Messages): Seq[Constraint[LocalDate]]
}

object DateConstraintHandlers extends Mappings {

  private val (april: Int, taxYearCloseDay: Int, taxYearOpenDay: Int) = (4, 5, 6)

  def localDateMappingWithDateRange[T](field: String = "value", date: T, outOfRangeKey: String = "genericDate.error.outsideReportedYear",
                                       invalidKey: String = "genericDate.error.invalid", dateDescription: String)
                                      (implicit messages: Messages, handler: DateConstraintHandler[T]): (String, Mapping[LocalDate]) =
    field -> localDate(invalidKey, dateDescription).verifying(firstError(withinDateRange(date, outOfRangeKey): _*))

  def withinDateRange[T](date: T, errorKey: String)
                        (implicit messages: Messages, handler: DateConstraintHandler[T]): Seq[Constraint[LocalDate]] =
    handler.handle(date, errorKey)

  implicit val intConstraintHandler: DateConstraintHandler[Int] = new DateConstraintHandler[Int] {
    def handle(date: Int, errorKey: String)(implicit messages: Messages): Seq[Constraint[LocalDate]] =
      Seq(
        yearHas4Digits("genericDate.error.invalid.year"),
        minDate(LocalDate.of(date, april, taxYearOpenDay), errorKey, date.toString, (date + 1).toString),
        maxDate(LocalDate.of(date + 1, april, taxYearCloseDay), errorKey, date.toString, (date + 1).toString)
      )
  }

  implicit val localDatesConstraintHandler: DateConstraintHandler[(LocalDate, LocalDate)] =
    new DateConstraintHandler[(LocalDate, LocalDate)] {
      def handle(date: (LocalDate, LocalDate), errorKey: String)(implicit messages: Messages): Seq[Constraint[LocalDate]] =
        Seq(
          yearHas4Digits("genericDate.error.invalid.year"),
          minDate(date._1, messages(errorKey, formatDateDMY(date._1), formatDateDMY(date._2)), "day", "month", "year"),
          maxDate(date._2, messages(errorKey, formatDateDMY(date._1), formatDateDMY(date._2)), "day", "month", "year")
        )
    }

  implicit val intAndLocalDateConstraintHandler: DateConstraintHandler[(Int, LocalDate)] =
    new DateConstraintHandler[(Int, LocalDate)] {
      def handle(date: (Int, LocalDate), errorKey: String)(implicit messages: Messages): Seq[Constraint[LocalDate]] =
        Seq(
          yearHas4Digits("genericDate.error.invalid.year"),
          minDate(LocalDate.of(date._1, april, taxYearOpenDay), errorKey, date._1.toString, (date._1 + 1).toString, "day", "month", "year"),
          maxDate(LocalDate.of(date._1 + 1, april, taxYearCloseDay), errorKey, date._1.toString, (date._1 + 1).toString, "day", "month", "year")
        )
    }
}
