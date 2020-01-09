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

package forms.chargeG

import base.SpecBase
import forms.behaviours._
import play.api.data.FormError

class ChargeAmountsFormProviderSpec extends SpecBase with DateBehaviours with BigDecimalFieldBehaviours with BooleanFieldBehaviours {

  val form = new ChargeAmountsFormProvider()("test name")
  val amountTransferredKey = "amountTransferred"
  val amountTaxDueKey = "amountTaxDue"

  "amountTransferred" must {

    behave like bigDecimalField(
      form = form,
      fieldName = amountTransferredKey,
      nonNumericError = FormError(amountTransferredKey, s"$amountTransferredKey.error.invalid"),
      decimalsError = FormError(amountTransferredKey, s"$amountTransferredKey.error.decimal")
    )
  }

  "amountTaxDue" must {

    behave like bigDecimalField(
      form = form,
      fieldName = amountTaxDueKey,
      nonNumericError = FormError(amountTaxDueKey, s"$amountTaxDueKey.error.invalid"),
      decimalsError = FormError(amountTaxDueKey, s"$amountTaxDueKey.error.decimal")
    )
  }
}
