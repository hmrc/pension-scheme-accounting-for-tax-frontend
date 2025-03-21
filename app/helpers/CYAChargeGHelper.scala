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
import models.chargeG.{ChargeAmounts, MemberDetails}
import models.{AccessType, CheckMode}
import play.api.i18n.Messages
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.{HtmlContent, Text}
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist._

import java.time.LocalDate

class CYAChargeGHelper(srn: String, startDate: LocalDate, accessType: AccessType, version: Int)(implicit messages: Messages) extends CYAHelper {

  def chargeGMemberDetails(index: Int, answer: MemberDetails): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(messages("chargeG.cya.MemberDetails.label")), classes = "govuk-!-width-one-half"),
        value = Value(HtmlContent(s"""<p class="govuk-body">${messages("cya.memberName.label")}:</p>
                                     |<p class="govuk-body">${answer.fullName}</p>
                                     |</br>
                                     |<p class="govuk-body">${messages("dob.cya.label", answer.fullName)}:</p>
                                     |<p class="govuk-body">${answer.dob.format(FormatHelper.dateFormatter)}</p>
                                     |</br>
                                     |<p class="govuk-body">${messages("cya.nino.label", answer.fullName)}:</p>
                                     |<p class="govuk-body">${answer.nino}</p>""".stripMargin), classes = "govuk-!-width-one-third"),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.chargeG.routes.MemberDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(messages("site.edit") + " " + messages("chargeG.visuallyHidden.memberDetails.label"))
            ))
          )
        )
      )
    )
  }

  def chargeGDetails(index: Int, answer: models.chargeG.ChargeDetails): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(messages("chargeG.chargeDetails.chargeDetails.label")), classes = "govuk-!-width-one-half"),
        value = Value(HtmlContent(s"""<p class="govuk-body">${messages("chargeG.chargeDetails.qropsReferenceNumber.label")}:</p>
                                     |<p class="govuk-body">${answer.qropsReferenceNumber}</p>
                                     |</br>
                                     |<p class="govuk-body">${messages("chargeG.chargeDetails.qropsTransferDate.label")}:</p>
                                     |<p class="govuk-body">${answer.qropsTransferDate.format(FormatHelper.dateFormatter)}</p>""".stripMargin),
          classes = "govuk-!-width-one-third"),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.chargeG.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(messages("site.edit") + " " + messages("chargeGDetails.chargeDetails.visuallyHidden.label"))
            ))
          )
        )
      )
    )
  }

  def chargeGAmounts(index: Int, answer: ChargeAmounts): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(messages("chargeG.chargeAmountDetails.transferred")), classes = "govuk-!-width-one-half"),
        value = Value(HtmlContent(s"""<p class="govuk-body">${messages("chargeG.chargeAmount.transferred")}:</p>
                                     |<p class="govuk-body">${FormatHelper.formatCurrencyAmountAsString(answer.amountTransferred)}</p>
                                     |</br>
                                     |<p class="govuk-body">${messages("chargeG.chargeAmount.taxDue")}:</p>
                                     |<p class="govuk-body">${FormatHelper.formatCurrencyAmountAsString(answer.amountTaxDue)}</p>""".stripMargin),
          classes = "govuk-!-width-one-third"),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.chargeG.routes.ChargeAmountsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(messages("site.edit") + " " + messages("chargeG.chargeAmountDetails.transferred.visuallyHidden.label"))
            ))
          )
        )
      )
    )
  }

}
