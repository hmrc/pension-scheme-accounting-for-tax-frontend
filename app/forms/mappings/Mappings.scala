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

package forms.mappings

import java.time.LocalDate

import models.Enumerable
import play.api.data.FieldMapping
import play.api.data.Forms.of

trait Mappings extends Formatters with Constraints with Transforms {

  protected def optionalText(): FieldMapping[Option[String]] =
    of(optionalStringFormatter)

  protected def optionalPostcode(requiredKey: String, invalidKey: String, countryFieldName: String): FieldMapping[Option[String]] =
    of(optionalPostcodeFormatter(requiredKey, invalidKey, countryFieldName))

  protected def text(errorKey: String = "error.required"): FieldMapping[String] =
    of(stringFormatter(errorKey))

  protected def int(requiredKey: String = "error.required",
                    wholeNumberKey: String = "error.wholeNumber",
                    nonNumericKey: String = "error.nonNumeric",
                    min: Option[(String, Int)] = None,
                    max: Option[(String, Int)] = None
                   ): FieldMapping[Int] =
    of(intFormatter(requiredKey, wholeNumberKey, nonNumericKey, min, max))

  protected def bigDecimal(requiredKey: String = "error.required",
                           invalidKey: String = "error.invalid"
                          ): FieldMapping[BigDecimal] =
    of(bigDecimalFormatter(requiredKey, invalidKey))

  protected def bigDecimal2DP(requiredKey: String = "error.required",
                              invalidKey: String = "error.invalid",
                              decimalKey: String = "error.decimal"
                             ): FieldMapping[BigDecimal] =
    of(bigDecimal2DPFormatter(requiredKey, invalidKey, decimalKey))

  protected def optionalBigDecimal2DP(otherKey: String,
                                      requiredKeyA: String = "error.required",
                                      requiredKeyB: String = "error.required",
                                      invalidKeyA: String = "error.invalid",
                                      invalidKeyB: String = "error.invalid",
                                      decimalKeyA: String = "error.decimal",
                                      decimalKeyB: String = "error.decimal"
                                     ): FieldMapping[Option[BigDecimal]] =
    of(conditionalBigDecimal2DPFormatter(otherKey, requiredKeyA, requiredKeyB, invalidKeyA, invalidKeyB, decimalKeyA, decimalKeyB))

  protected def bigDecimalTotal(itemsToTotal: String*): FieldMapping[BigDecimal] =
    of(bigDecimalTotalFormatter(itemsToTotal: _*))

  protected def boolean(requiredKey: String = "error.required",
                        invalidKey: String = "error.boolean"): FieldMapping[Boolean] =
    of(booleanFormatter(requiredKey, invalidKey))


  protected def enumerable[A](requiredKey: String = "error.required",
                              invalidKey: String = "error.invalid")(implicit ev: Enumerable[A]): FieldMapping[A] =
    of(enumerableFormatter[A](requiredKey, invalidKey))

  protected def localDate(invalidKey: String,
                          allRequiredKey: String,
                          twoRequiredKey: String,
                          requiredKey: String,
                          args: Seq[String] = Seq.empty): FieldMapping[LocalDate] =
    of(new LocalDateFormatter(invalidKey, allRequiredKey, twoRequiredKey, requiredKey, args))
}
