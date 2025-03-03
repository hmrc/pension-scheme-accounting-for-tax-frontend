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
import models.chargeB.ChargeBDetails
import models.{AccessType, CheckMode}
import play.api.i18n.Messages
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.{HtmlContent, Text}
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{ActionItem, Actions, Key, SummaryListRow, Value}

import java.time.LocalDate

class CYAChargeBHelper(srn: String, startDate: LocalDate, accessType: AccessType, version: Int)(implicit messages: Messages) extends CYAHelper {

  def chargeBDetails(answer: ChargeBDetails): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(messages("chargeB.deathBenefits.checkYourAnswersLabel")), classes = "govuk-!-width-one-half"),
        value = Value(HtmlContent(s"""<p class="govuk-body">${messages("chargeB.numberOfDeceased.checkYourAnswersLabel")}:</p>
                                     |<p class="govuk-body">${answer.numberOfDeceased.toString}</p>
                                     |</br>
                                     |<p class="govuk-body">${messages("chargeB.totalTaxDue.checkYourAnswersLabel")}:</p>
                                     |<p class="govuk-body">${FormatHelper.formatCurrencyAmountAsString(answer.totalAmount)}</p>""".stripMargin), classes = "govuk-!-width-one-quarter"),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.chargeB.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version).url,
              visuallyHiddenText = Some(
                messages("site.edit") + " " + messages("chargeB.deathBenefits.visuallyHidden.checkYourAnswersLabel")
              )
            ))
          )
        )
      )
    )
  }

}
