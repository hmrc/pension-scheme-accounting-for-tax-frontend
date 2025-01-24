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

package helpers

import models.YearRange
import play.api.i18n.Messages
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{Key, SummaryListRow, Value}

trait CYAHelper {

  def total(total: BigDecimal)(implicit messages: Messages): SummaryListRow = SummaryListRow(
    key = Key(Text(messages("total")), classes = "govuk-!-width-one-half govuk-table__cell--numeric govuk-!-font-weight-bold"),
    value = Value(Text(s"${FormatHelper.formatCurrencyAmountAsString(total)}"))
  )

  def yesOrNo(answer: Boolean)(implicit messages: Messages): Text =
    if (answer) Text(messages("site.yes")) else Text(messages("site.no"))

  def rows(viewOnly: Boolean, rows: Seq[SummaryListRow]): Seq[SummaryListRow] = {
    if (viewOnly) rows.map(_.copy(actions = None)) else rows
  }

  def getLabel(yr: YearRange)(implicit messages: Messages): Text = {
    val startYear = yr.toString
    val yearRangeMsg = messages("yearRangeRadio", startYear, (startYear.toInt + 1).toString)
    Text(yearRangeMsg)
  }
}
