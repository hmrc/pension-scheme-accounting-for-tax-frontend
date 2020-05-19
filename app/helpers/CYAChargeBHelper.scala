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

import models.CheckMode
import models.LocalDateBinder._
import models.chargeB.ChargeBDetails
import play.api.i18n.Messages
import uk.gov.hmrc.viewmodels.SummaryList.{Action, Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels._

class CYAChargeBHelper(srn: String, startDate: LocalDate)(implicit messages: Messages) extends CYAHelper {

  def chargeBDetails(answer: ChargeBDetails): Seq[Row] = {
    Seq(
      Row(
        key = Key(msg"chargeB.numberOfDeceased.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.numberOfDeceased.toString), classes = Seq("govuk-!-width-one-quarter")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeB.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate).url,
            visuallyHiddenText = Some(msg"chargeB.numberOfDeceased.visuallyHidden.checkYourAnswersLabel")
          )
        )
      ),
      Row(
        key = Key(msg"chargeB.totalTaxDue.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(answer.totalAmount)}"), classes = Seq("govuk-!-width-one-quarter")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeB.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate).url,
            visuallyHiddenText = Some(msg"chargeB.totalTaxDue.visuallyHidden.checkYourAnswersLabel")
          )
        )
      )
    )
  }

}
