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

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.{Currency, Locale}

import helpers.CYAHelper._
import models.LocalDateBinder._
import models.chargeB.ChargeBDetails
import models.chargeD.ChargeDDetails
import models.chargeE.ChargeEDetails
import models.chargeF.ChargeDetails
import models.chargeG.{ChargeAmounts, MemberDetails}
import models.{CheckMode, UserAnswers, YearRange}
import play.api.i18n.Messages
import uk.gov.hmrc.viewmodels.SummaryList._
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels._

case object DataMissingException extends Exception

class CYAHelper(userAnswers: UserAnswers, srn: String, startDate: LocalDate)(implicit messages: Messages) {

  def chargeFDate(answer: ChargeDetails): Row =
    Row(
      key = Key(msg"chargeF.chargeDetails.date.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
      value = Value(Literal(answer.deRegistrationDate.format(dateFormatter)), classes = Seq("govuk-!-width-one-quarter")),
      actions = List(
        Action(
          content = msg"site.edit",
          href = controllers.chargeF.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate).url,
          visuallyHiddenText = Some(msg"chargeF.chargeDetails.date.visuallyHidden.checkYourAnswersLabel")
        )
      )
    )


  def chargeFAmount(answer: ChargeDetails): Row =
    Row(
      key = Key(msg"chargeF.chargeDetails.amount.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
      value = Value(Literal(s"${formatCurrencyAmountAsString(answer.amountTaxDue)}"), classes = Seq("govuk-!-width-one-quarter")),
      actions = List(
        Action(
          content = msg"site.edit",
          href = controllers.chargeF.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate).url,
          visuallyHiddenText = Some(msg"chargeF.chargeDetails.amount.visuallyHidden.checkYourAnswersLabel")
        )
      )
    )

  def chargeAMembers(answer: models.chargeA.ChargeDetails): Row = {
    Row(
      key = Key(msg"chargeA.chargeDetails.numberOfMembers.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
      value = Value(Literal(answer.numberOfMembers.toString), classes = Seq("govuk-!-width-one-quarter")),
      actions = List(
        Action(
          content = msg"site.edit",
          href = controllers.chargeA.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate).url,
          visuallyHiddenText = Some(msg"chargeA.chargeDetails.numberOfMembers.visuallyHidden.checkYourAnswersLabel")
        )
      )
    )
  }

  def chargeAAmountLowerRate(answer: models.chargeA.ChargeDetails): Row = {
    Row(
      key = Key(msg"chargeA.chargeDetails.amountLowerRate.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
      value = Value(Literal(s"${formatCurrencyAmountAsString(answer.totalAmtOfTaxDueAtLowerRate.getOrElse(BigDecimal(0.00)))}"),
        classes = Seq("govuk-!-width-one-quarter")),
      actions = List(
        Action(
          content = msg"site.edit",
          href = controllers.chargeA.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate).url,
          visuallyHiddenText = Some(msg"chargeA.chargeDetails.amountLowerRate.visuallyHidden.checkYourAnswersLabel")
        )
      )
    )
  }

  def chargeAAmountHigherRate(answer: models.chargeA.ChargeDetails): Row = {
    Row(
      key = Key(msg"chargeA.chargeDetails.amountHigherRate.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
      value = Value(Literal(s"${formatCurrencyAmountAsString(answer.totalAmtOfTaxDueAtHigherRate.getOrElse(BigDecimal(0.00)))}"),
        classes = Seq("govuk-!-width-one-quarter")),
      actions = List(
        Action(
          content = msg"site.edit",
          href = controllers.chargeA.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate).url,
          visuallyHiddenText = Some(msg"chargeA.chargeDetails.amountHigherRate.visuallyHidden.checkYourAnswersLabel")
        )
      )
    )
  }

  def total(total: BigDecimal): Row = Row(
    key = Key(msg"total", classes = Seq("govuk-!-width-one-half", "govuk-table__cell--numeric", "govuk-!-font-weight-bold")),
    value = Value(Literal(s"${formatCurrencyAmountAsString(total)}"))
  )

  def chargeBDetails(answer: ChargeBDetails): Seq[Row] = {
    Seq(
      Row(
        key = Key(msg"chargeB.numberOfDeceased.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.numberOfDeceased.toString), classes = Seq("govuk-!-width-one-quarter")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeB.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate).url,
            visuallyHiddenText = Some(msg"chargeB.numberOfDeceased.visuallyHidden.checkYourAnswersLabel")
          )
        )
      ),
      Row(
        key = Key(msg"chargeB.totalTaxDue.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(s"${formatCurrencyAmountAsString(answer.amountTaxDue)}"), classes = Seq("govuk-!-width-one-quarter")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeB.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate).url,
            visuallyHiddenText = Some(msg"chargeB.totalTaxDue.visuallyHidden.checkYourAnswersLabel")
          )
        )
      )
    )
  }

  def chargeEMemberDetails(index: Int, answer: models.MemberDetails): Seq[Row] = {
    Seq(
      Row(
        key = Key(msg"cya.memberName.label", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.fullName.toString), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeE.routes.MemberDetailsController.onPageLoad(CheckMode, srn, startDate, index).url,
            visuallyHiddenText = Some(msg"visuallyHidden.memberName.label")
          )
        )
      ),
      Row(
        key = Key(msg"cya.nino.label".withArgs(answer.fullName), classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.nino), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeE.routes.MemberDetailsController.onPageLoad(CheckMode, srn, startDate, index).url,
            visuallyHiddenText = Some(msg"cya.nino.label".withArgs(answer.fullName))
          )
        )
      )
    )
  }

  def chargeETaxYear(index: Int, answer: YearRange): Seq[Row] = {
    Seq(
      Row(
        key = Key(msg"chargeE.cya.taxYear.label", classes = Seq("govuk-!-width-one-half")),
        value = Value(YearRange.getLabel(answer), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeE.routes.AnnualAllowanceYearController.onPageLoad(CheckMode, srn, startDate, index).url,
            visuallyHiddenText = Some(msg"chargeE.visuallyHidden.taxYear.label")
          )
        )
      )
    )
  }


  def chargeEDetails(index: Int, answer: ChargeEDetails): Seq[Row] = {
    Seq(
      Row(
        key = Key(msg"chargeEDetails.chargeAmount.label", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(s"${formatCurrencyAmountAsString(answer.chargeAmount)}"), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeE.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, index).url,
            visuallyHiddenText = Some(msg"chargeE.visuallyHidden.chargeAmount.label")
          )
        )
      ),
      Row(
        key = Key(msg"chargeEDetails.dateNoticeReceived.label", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.dateNoticeReceived.format(dateFormatter)), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeE.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, index).url,
            visuallyHiddenText = Some(msg"chargeE.visuallyHidden.dateNoticeReceived.label")
          )
        )
      ),
      Row(
        key = Key(msg"chargeE.cya.mandatoryPayment.label", classes = Seq("govuk-!-width-one-half")),
        value = Value(yesOrNo(answer.isPaymentMandatory), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeE.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, index).url,
            visuallyHiddenText = Some(msg"chargeE.visuallyHidden.isPaymentMandatory.label")
          )
        )
      )
    )
  }







}

object CYAHelper {

  val currencyFormatter: NumberFormat = {
    val cf = java.text.NumberFormat.getCurrencyInstance(new Locale("en", "GB"))
    cf.setCurrency(Currency.getInstance(new Locale("en", "GB")))
    cf
  }

  val dateFormatter = DateTimeFormatter.ofPattern("d/M/yyyy")

  def formatCurrencyAmountAsString(bd: BigDecimal): String = currencyFormatter.format(bd)

  def yesOrNo(answer: Boolean): Content =
    if (answer) msg"site.yes" else msg"site.no"

  def rows(viewOnly: Boolean, rows: Seq[SummaryList.Row]): Seq[SummaryList.Row] = {
    if (viewOnly) rows.map(_.copy(actions = Nil)) else rows
  }
}
