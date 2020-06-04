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

package helpers

import java.time.LocalDate

import models.{AccessType, CheckMode}
import models.LocalDateBinder._
import models.chargeF.ChargeDetails
import play.api.i18n.Messages
import uk.gov.hmrc.viewmodels.SummaryList.{Action, Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels._

class CYAChargeFHelper(srn: String, startDate: LocalDate, accessType: AccessType, version: Int)(implicit messages: Messages) extends CYAHelper {

  def chargeFDate(answer: ChargeDetails): Row =
    Row(
      key = Key(msg"chargeF.chargeDetails.date.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
      value = Value(Literal(answer.deRegistrationDate.format(FormatHelper.dateFormatter)), classes = Seq("govuk-!-width-one-quarter")),
      actions = List(
        Action(
          content = msg"site.edit",
          href = controllers.chargeF.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version).url,
          visuallyHiddenText = Some(msg"chargeF.chargeDetails.date.visuallyHidden.checkYourAnswersLabel")
        )
      )
    )

  def chargeFAmount(answer: ChargeDetails): Row =
    Row(
      key = Key(msg"chargeF.chargeDetails.amount.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
      value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(answer.totalAmount)}"), classes = Seq("govuk-!-width-one-quarter")),
      actions = List(
        Action(
          content = msg"site.edit",
          href = controllers.chargeF.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version).url,
          visuallyHiddenText = Some(msg"chargeF.chargeDetails.amount.visuallyHidden.checkYourAnswersLabel")
        )
      )
    )
}
