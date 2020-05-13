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
import controllers.chargeB.{routes => _}
import models.UserAnswers
import play.api.i18n.Messages
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels._

class AmendmentHelper {

  def getTotalAmount(ua: UserAnswers): (BigDecimal, BigDecimal) = {
    val amountUK = Seq(
      ua.get(pages.chargeE.TotalChargeAmountPage).getOrElse(BigDecimal(0)),
      ua.get(pages.chargeC.TotalChargeAmountPage).getOrElse(BigDecimal(0)),
      ua.get(pages.chargeF.ChargeDetailsPage).map(_.totalAmount).getOrElse(BigDecimal(0)),
      ua.get(pages.chargeD.TotalChargeAmountPage).getOrElse(BigDecimal(0)),
      ua.get(pages.chargeA.ChargeDetailsPage).map(_.totalAmount).getOrElse(BigDecimal(0)),
      ua.get(pages.chargeB.ChargeBDetailsPage).map(_.totalAmount).getOrElse(BigDecimal(0))
    ).sum

    val amountNonUK = ua.get(pages.chargeG.TotalChargeAmountPage).getOrElse(BigDecimal(0))

    (amountUK, amountNonUK)
  }

  def amendmentSummaryRows(currentTotalAmount: BigDecimal, previousTotalAmount: BigDecimal, currentVersion: Int, previousVersion: Int)(
      implicit messages: Messages): Seq[Row] = {
    val differenceAmount = currentTotalAmount - previousTotalAmount
    if (previousTotalAmount == 0 && currentTotalAmount == 0) {
      Nil
    } else {
      Seq(
        Row(
          key = Key(msg"confirmSubmitAFTReturn.total.for".withArgs(previousVersion), classes = Seq("govuk-!-width-three-quarters")),
          value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(previousTotalAmount)}"),
                        classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")),
          actions = Nil
        ),
        Row(
          key = Key(msg"confirmSubmitAFTReturn.total.for.draft", classes = Seq("govuk-!-width-three-quarters")),
          value = Value(
            Literal(s"${FormatHelper.formatCurrencyAmountAsString(currentTotalAmount)}"),
            classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")
          ),
          actions = Nil
        ),
        Row(
          key = Key(msg"confirmSubmitAFTReturn.difference", classes = Seq("govuk-!-width-three-quarters")),
          value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(differenceAmount)}"),
                        classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")),
          actions = Nil
        )
      )
    }
  }
}
