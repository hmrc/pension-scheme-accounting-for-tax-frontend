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

import models.ChargeType.ChargeTypeLifetimeAllowance
import models.LocalDateBinder._
import models.chargeD.ChargeDDetails
import models.{AccessType, CheckMode}
import play.api.i18n.Messages
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.{HtmlContent, Text}
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{ActionItem, Actions, Key, SummaryListRow, Value}

import java.time.LocalDate

class CYAChargeDHelper(srn: String, startDate: LocalDate, accessType: AccessType, version: Int)(implicit messages: Messages)
  extends CYAPublicPensionsRemedyHelper(srn, startDate, accessType, version, ChargeTypeLifetimeAllowance) {

  def chargeDMemberDetails(index: Int, answer: models.MemberDetails): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(messages("cya.memberName.label")), classes = "govuk-!-width-one-half"),
        value = Value(Text(answer.fullName), classes = "govuk-!-width-one-third"),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.chargeD.routes.MemberDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
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
              href = controllers.chargeD.routes.MemberDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(messages("site.edit") + " " + messages("cya.nino.label", answer.fullName))
            ))
          )
        )
      )
    )
  }

  def chargeDDetails(index: Int, answer: ChargeDDetails): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(messages("chargeDDetails.dateOfEvent.label")), classes = "govuk-!-width-one-half"),
        value = Value(Text(answer.dateOfEvent.format(FormatHelper.dateFormatter)), classes = "govuk-!-width-one-third"),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.chargeD.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(messages("site.edit") + " " + messages("chargeDDetails.dateOfEvent.visuallyHidden.label"))
            ))
          )
        )
      ),
      SummaryListRow(
        key = Key(Text(messages("taxAt25Percent.label")), classes = "govuk-!-width-one-half"),
        value = Value(Text(s"${FormatHelper.formatCurrencyAmountAsString(answer.taxAt25Percent.getOrElse(BigDecimal(0.00)))}"),
          classes = "govuk-!-width-one-third"),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.chargeD.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(messages("site.edit") + " " + messages("taxAt25Percent.visuallyHidden.label"))
            ))
          )
        )
      ),
      SummaryListRow(
        key = Key(Text(messages("taxAt55Percent.label")), classes = "govuk-!-width-one-half"),
        value = Value(Text(s"${FormatHelper.formatCurrencyAmountAsString(answer.taxAt55Percent.getOrElse(BigDecimal(0.00)))}"),
          classes = "govuk-!-width-one-third"),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.chargeD.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(
                messages("site.edit") + " " + messages("taxAt55Percent.visuallyHidden.label")
              )
            ))
          )
        )
      )
    )
  }

  def isPsprForChargeD(isPsrAlwaysTrue : Boolean, index: Int, isPSR: Option[Boolean]): Seq[SummaryListRow] = {
    isPsrAlwaysTrue match {
      case true => Nil
      case _ => isPsprForCharge(index, isPSR)
    }
  }
}
