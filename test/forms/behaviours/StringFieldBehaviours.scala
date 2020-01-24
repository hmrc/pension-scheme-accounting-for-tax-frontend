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

package forms.behaviours

import play.api.data.{Form, FormError}

trait StringFieldBehaviours extends FieldBehaviours {

  def fieldWithMaxLength(form: Form[_],
                         fieldName: String,
                         maxLength: Int,
                         lengthError: FormError): Unit = {

    s"must not bind strings longer than $maxLength characters" in {

      forAll(stringsLongerThan(maxLength) -> "longString") {
        string =>
          val result = form.bind(Map(fieldName -> string)).apply(fieldName)
          result.errors.head.message mustEqual lengthError.message
          result.errors.head.key mustEqual lengthError.key
      }
    }
  }

  def fieldWithMinLength(form: Form[_],
                         fieldName: String,
                         minLength: Int,
                         lengthError: FormError): Unit = {

    s"must not bind strings shorter than $minLength characters" in {

      forAll(stringsShorterThan(minLength) -> "longString") {
        string =>
          val result = form.bind(Map(fieldName -> string)).apply(fieldName)
          result.errors.head.message mustEqual lengthError.message
          result.errors.head.key mustEqual lengthError.key
      }
    }
  }

  def fieldWithRegex(form: Form[_],
                     fieldName: String,
                     invalidValues: Seq[String],
                     invalidError: FormError): Unit = {

    invalidValues.foreach { invalidVal =>
      s"not bind string $invalidVal invalidated by regex" in {
        val result = form.bind(Map(fieldName -> invalidVal)).apply(fieldName)
        result.errors mustEqual Seq(invalidError)
      }
    }
  }

  def nino(form: Form[_],
           fieldName: String,
           requiredKey: String,
           invalidKey: String): Unit = {

    behave like mandatoryField(
      form,
      fieldName,
      requiredError = FormError(fieldName, requiredKey)
    )

    "successfully bind when yes is selected and valid NINO is provided" in {
      val res = form.bind(Map("nino" -> "AB020202A")).apply("nino")
      res.value.get mustEqual "AB020202A"
    }

    Seq("DE999999A", "AO111111B", "ORA12345C", "AB0202020", "AB0303030D", "AB040404E").foreach { nino =>
      s"fail to bind when NINO $nino is invalid" in {
        val result = form.bind(Map("nino" -> nino)).apply("nino")
        result.errors mustBe Seq(FormError("nino", invalidKey))
      }
    }
  }

  def qrOps(form: Form[_],
            fieldName: String,
            requiredKey: String,
            invalidKey: String): Unit = {

    behave like mandatoryField(
      form,
      fieldName,
      requiredError = FormError(fieldName, requiredKey)
    )

    "successfully bind when valid QROPS is provided" in {
      val res = form.bind(Map(fieldName -> "123123")).apply(fieldName)
      res.value.get mustEqual "123123"
    }

    Seq("A123789", "ABCDEF3", "1122334").foreach { qrops =>
      s"fail to bind when QROPS $qrops is invalid" in {
        val result = form.bind(Map(fieldName -> qrops)).apply(fieldName)
        result.errors.head.key mustBe fieldName
        result.errors.head.message mustBe invalidKey
      }
    }
  }
}
