/*
 * Copyright 2023 HM Revenue & Customs
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

import models.LocalDateBinder._
import models.{AccessType, CheckMode}
import play.api.i18n.Messages
import uk.gov.hmrc.viewmodels.SummaryList.{Action, Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels._

import java.time.LocalDate

class CYAChargeAHelper(srn: String, startDate: LocalDate, accessType: AccessType, version: Int)(implicit messages: Messages) extends CYAHelper {

  def chargeAMembers(answer: models.chargeA.ChargeDetails): Row = {
    Row(
      key = Key(msg"chargeA.chargeDetails.numberOfMembers.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
      value = Value(Literal(answer.numberOfMembers.toString), classes = Seq("govuk-!-width-one-quarter")),
      actions = List(
        Action(
          content = Html(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
          href = controllers.chargeA.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version).url,
          visuallyHiddenText = Some(Literal(
            messages("site.edit") + " " + messages("chargeA.chargeDetails.numberOfMembers.visuallyHidden.checkYourAnswersLabel")
          ))
        )
      )
    )
  }

  def chargeAAmountLowerRate(answer: models.chargeA.ChargeDetails): Row = {
    Row(
      key = Key(msg"chargeA.chargeDetails.amountLowerRate.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
      value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(answer.totalAmtOfTaxDueAtLowerRate.getOrElse(BigDecimal(0.00)))}"),
        classes = Seq("govuk-!-width-one-quarter")),
      actions = List(
        Action(
          content = Html(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
          href = controllers.chargeA.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version).url,
          visuallyHiddenText = Some(Literal(
            messages("site.edit") + " " + messages("chargeA.chargeDetails.amountLowerRate.visuallyHidden.checkYourAnswersLabel")
          ))
        )
      )
    )
  }

  def chargeAAmountHigherRate(answer: models.chargeA.ChargeDetails): Row = {
    Row(
      key = Key(msg"chargeA.chargeDetails.amountHigherRate.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
      value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(answer.totalAmtOfTaxDueAtHigherRate.getOrElse(BigDecimal(0.00)))}"),
        classes = Seq("govuk-!-width-one-quarter")),
      actions = List(
        Action(
          content = Html(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
          href = controllers.chargeA.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version).url,
          visuallyHiddenText = Some(Literal(
            messages("site.edit") + " " + messages("chargeA.chargeDetails.amountHigherRate.visuallyHidden.checkYourAnswersLabel")
          ))
        )
      )
    )
  }

}
