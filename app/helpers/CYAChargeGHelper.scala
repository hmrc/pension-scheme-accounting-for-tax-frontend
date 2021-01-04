/*
 * Copyright 2021 HM Revenue & Customs
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

import models.{AccessType, CheckMode}
import models.LocalDateBinder._
import models.chargeG.{ChargeAmounts, MemberDetails}
import play.api.i18n.Messages
import uk.gov.hmrc.viewmodels.SummaryList.{Action, Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels._

class CYAChargeGHelper(srn: String, startDate: LocalDate, accessType: AccessType, version: Int)(implicit messages: Messages) extends CYAHelper {

  def chargeGMemberDetails(index: Int, answer: MemberDetails): Seq[Row] = {
    Seq(
      Row(
        key = Key(msg"cya.memberName.label", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.fullName.toString), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = Html(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
            href = controllers.chargeG.routes.MemberDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
            visuallyHiddenText = Some(Literal(
              messages("site.edit") + " " + messages("visuallyHidden.memberName.label")
            ))
          )
        )
      ),
      Row(
        key = Key(msg"dob.cya.label".withArgs(answer.fullName), classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.dob.format(FormatHelper.dateFormatter)), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = Html(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
            href = controllers.chargeG.routes.MemberDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
            visuallyHiddenText = Some(Literal(
              messages("site.edit") + " " + messages("dob.cya.label",answer.fullName)
            ))
          )
        )
      ),
      Row(
        key = Key(msg"cya.nino.label".withArgs(answer.fullName), classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.nino), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = Html(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
            href = controllers.chargeG.routes.MemberDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
            visuallyHiddenText = Some(Literal(
              messages("site.edit") + " " + messages("cya.nino.label", answer.fullName)
            ))
          )
        )
      )
    )
  }

  def chargeGDetails(index: Int, answer: models.chargeG.ChargeDetails): Seq[Row] = {
    Seq(
      Row(
        key = Key(msg"chargeG.chargeDetails.qropsReferenceNumber.label", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.qropsReferenceNumber), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = Html(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
            href = controllers.chargeG.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
            visuallyHiddenText = Some(Literal(
              messages("site.edit") + " " + messages("chargeGDetails.qropsReferenceNumber.visuallyHidden.label")
            ))
          )
        )
      ),
      Row(
        key = Key(msg"chargeG.chargeDetails.qropsTransferDate.label", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.qropsTransferDate.format(FormatHelper.dateFormatter)), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = Html(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
            href = controllers.chargeG.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
            visuallyHiddenText = Some(Literal(
              messages("site.edit") + " " + messages("chargeGDetails.qropsTransferDate.visuallyHidden.label")
            ))
          )
        )
      )
    )
  }

  def chargeGAmounts(index: Int, answer: ChargeAmounts): Seq[Row] = {
    Seq(
      Row(
        key = Key(msg"chargeG.chargeAmount.transferred", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(answer.amountTransferred)}"), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = Html(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
            href = controllers.chargeG.routes.ChargeAmountsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
            visuallyHiddenText = Some(Literal(
              messages("site.edit") + " " + messages("chargeG.chargeAmount.transferred.visuallyHidden.label")
            ))
          )
        )
      ),
      Row(
        key = Key(msg"chargeG.chargeAmount.taxDue", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(answer.amountTaxDue)}"), classes = Seq("govuk-!-width-one-thirdt run" +
          "")),
        actions = List(
          Action(
            content = Html(s"<span aria-hidden=true>${messages("site.edit")}</span>"),
            href = controllers.chargeG.routes.ChargeAmountsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
            visuallyHiddenText = Some(Literal(
              messages("site.edit") + " " + messages("chargeG.chargeAmount.taxDue.visuallyHidden.label")
            ))
          )
        )
      )
    )
  }

}
