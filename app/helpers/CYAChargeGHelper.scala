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
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{ActionItem, Actions, Key, SummaryListRow, Value}

import java.time.LocalDate

class CYAChargeGHelper(srn: String, startDate: LocalDate, accessType: AccessType, version: Int)(implicit messages: Messages) extends CYAHelper {

  def chargeGMemberDetails(index: Int, answer: MemberDetails): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(messages("cya.memberName.label")), classes = "govuk-!-width-one-half"),
        value = Value(Text(answer.fullName.toString), classes = "govuk-!-width-one-third"),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.chargeG.routes.MemberDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(messages("site.edit") + " " + messages("visuallyHidden.memberName.label"))
            ))

          )
        )
      ),
      SummaryListRow(
        key = Key(Text(messages("dob.cya.label", answer.fullName)), classes = "govuk-!-width-one-half"),
        value = Value(Text(answer.dob.format(FormatHelper.dateFormatter)), classes = "govuk-!-width-one-third"),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.chargeG.routes.MemberDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(messages("site.edit") + " " + messages("dob.cya.label",answer.fullName))
            ))
          )
        )
      ),
      SummaryListRow(
        key = Key(Text(messages("cya.nino.label", answer.fullName)), classes = "govuk-!-width-one-half"),
        value = Value(Text(answer.nino), classes = "govuk-!-width-one-third"),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.chargeG.routes.MemberDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(messages("site.edit") + " " + messages("cya.nino.label", answer.fullName))
            ))
          )
        )
      )
    )
  }

  def chargeGDetails(index: Int, answer: models.chargeG.ChargeDetails): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(messages("chargeG.chargeDetails.qropsReferenceNumber.label")), classes = "govuk-!-width-one-half"),
        value = Value(Text(answer.qropsReferenceNumber), classes = "govuk-!-width-one-third"),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.chargeG.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(messages("site.edit") + " " + messages("chargeGDetails.qropsReferenceNumber.visuallyHidden.label"))
            ))
          )
        )
      ),
      SummaryListRow(
        key = Key(Text(messages("chargeG.chargeDetails.qropsTransferDate.label")), classes = "govuk-!-width-one-half"),
        value = Value(Text(answer.qropsTransferDate.format(FormatHelper.dateFormatter)), classes = "govuk-!-width-one-third"),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.chargeG.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(messages("site.edit") + " " + messages("chargeGDetails.qropsTransferDate.visuallyHidden.label"))
            ))
          )
        )
      )
    )
  }

  def chargeGAmounts(index: Int, answer: ChargeAmounts): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(messages("chargeG.chargeAmount.transferred")), classes = "govuk-!-width-one-half"),
        value = Value(Text(s"${FormatHelper.formatCurrencyAmountAsString(answer.amountTransferred)}"), classes = "govuk-!-width-one-third"),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.chargeG.routes.ChargeAmountsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(messages("site.edit") + " " + messages("chargeG.chargeAmount.transferred.visuallyHidden.label"))
            ))
          )
        )
      ),
      SummaryListRow(
        key = Key(Text(messages("chargeG.chargeAmount.taxDue")), classes = "govuk-!-width-one-half"),
        value = Value(Text(s"${FormatHelper.formatCurrencyAmountAsString(answer.amountTaxDue)}"), classes = "govuk-!-width-one-thirdt run" + ""),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span aria-hidden=true>${messages("site.edit")}</span>"),
              href = controllers.chargeG.routes.ChargeAmountsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(messages("site.edit") + " " + messages("chargeG.chargeAmount.taxDue.visuallyHidden.label"))
            ))
          )
        )
      )
    )
  }

}
