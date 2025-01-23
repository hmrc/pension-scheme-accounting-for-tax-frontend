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

import models.ChargeType.ChargeTypeAnnualAllowance
import models.LocalDateBinder._
import models.chargeE.ChargeEDetails
import models.{AccessType, CheckMode, YearRange}
import play.api.i18n.Messages
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.{HtmlContent, Text}
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{ActionItem, Actions, Key, SummaryListRow, Value}

import java.time.LocalDate

class CYAChargeEHelper(srn: String, startDate: LocalDate, accessType: AccessType, version: Int)(implicit messages: Messages)
  extends CYAPublicPensionsRemedyHelper(srn, startDate, accessType, version, ChargeTypeAnnualAllowance) {


  def chargeEMemberDetails(index: Int, answer: models.MemberDetails): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(messages("cya.memberName.label")), classes = "govuk-!-width-one-half"),
        value = Value(Text(answer.fullName), classes = "govuk-!-width-one-third"),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.chargeE.routes.MemberDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(messages("site.edit") + " " + messages("visuallyHidden.memberName.label"))
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
              href = controllers.chargeE.routes.MemberDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(messages("site.edit") + " " + messages("cya.nino.label", answer.fullName))
            ))
          )
        )
      )
    )
  }

  def chargeETaxYear(index: Int, answer: YearRange): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(messages("chargeE.cya.taxYear.label")), classes = "govuk-!-width-one-half"),
        value = Value(getLabel(answer), classes = "govuk-!-width-one-third"),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.chargeE.routes.AnnualAllowanceYearController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(messages("site.edit") + " " + messages("chargeE.visuallyHidden.taxYear.label"))
            ))
          )
        )
      )
    )
  }

  def chargeEDetails(index: Int, answer: ChargeEDetails): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(messages("chargeEDetails.chargeAmount.label")), classes = "govuk-!-width-one-half"),
        value = Value(Text(s"${FormatHelper.formatCurrencyAmountAsString(answer.chargeAmount)}"), classes = "govuk-!-width-one-third"),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.chargeE.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(messages("site.edit") + " " + messages("chargeE.visuallyHidden.chargeAmount.label"))
            ))
          )
        )
      ),
      SummaryListRow(
        key = Key(Text(messages("chargeEDetails.dateNoticeReceived.label")), classes = "govuk-!-width-one-half"),
        value = Value(Text(answer.dateNoticeReceived.format(FormatHelper.dateFormatter)), classes = "govuk-!-width-one-third"),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.chargeE.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(messages("site.edit") + " " + messages("chargeE.visuallyHidden.dateNoticeReceived.label"))
            ))
          )
        )
      ),
      SummaryListRow(
        key = Key(Text(messages("chargeE.cya.mandatoryPayment.label")), classes = "govuk-!-width-one-half"),
        value = Value(yesOrNo(answer.isPaymentMandatory), classes = "govuk-!-width-one-third"),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.chargeE.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(messages("site.edit") + " " + messages("chargeE.visuallyHidden.isPaymentMandatory.label"))
            ))
          )
        )
      )
    )
  }
}
