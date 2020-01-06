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
          result.errors.head.message shouldEqual lengthError.message
          result.errors.head.key shouldEqual lengthError.key
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
      res.value.get shouldEqual "AB020202A"
    }

    Seq("DE999999A", "AO111111B", "ORA12345C", "AB0202020", "AB0303030D", "AB040404E").foreach { nino =>
      s"fail to bind when NINO $nino is invalid" in {
        val result = form.bind(Map("nino" -> nino)).apply("nino")
        result.errors shouldBe Seq(FormError("nino", invalidKey))
      }
    }
  }
}
