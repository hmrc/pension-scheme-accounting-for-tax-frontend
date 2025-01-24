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

import models.LocalDateBinder._
import models.chargeF.ChargeDetails
import models.{AccessType, CheckMode}
import play.api.i18n.Messages
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{ActionItem, Actions, Key, SummaryListRow, Value}
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.{HtmlContent, Text}

import java.time.LocalDate

class CYAChargeFHelper(srn: String, startDate: LocalDate, accessType: AccessType, version: Int)(implicit messages: Messages) extends CYAHelper {

  def chargeFDate(answer: ChargeDetails): SummaryListRow =
    SummaryListRow(
      key = Key(Text(messages("chargeF.chargeDetails.date.checkYourAnswersLabel")), classes = "govuk-!-width-one-half"),
      value = Value(Text(answer.deRegistrationDate.format(FormatHelper.dateFormatter)), classes = "govuk-!-width-one-quarter"),
      actions = Some(
        Actions(
          items = Seq(ActionItem(
            content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
            href = controllers.chargeF.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version).url,
            visuallyHiddenText = Some(
              messages("site.edit") + " " + messages("chargeF.chargeDetails.date.visuallyHidden.checkYourAnswersLabel")
            )
          ))
        )
      )
    )

  def chargeFAmount(answer: ChargeDetails): SummaryListRow =
    SummaryListRow(
      key = Key(Text(messages("chargeF.chargeDetails.amount.checkYourAnswersLabel")), classes = "govuk-!-width-one-half"),
      value = Value(Text(s"${FormatHelper.formatCurrencyAmountAsString(answer.totalAmount)}"), classes = "govuk-!-width-one-quarter"),
      actions = Some(
        Actions(
          items = Seq(ActionItem(
            content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
            href = controllers.chargeF.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version).url,
            visuallyHiddenText = Some(messages("site.edit") + " " + messages("chargeF.chargeDetails.amount.visuallyHidden.checkYourAnswersLabel"))
          ))
        )
      )
    )
}
