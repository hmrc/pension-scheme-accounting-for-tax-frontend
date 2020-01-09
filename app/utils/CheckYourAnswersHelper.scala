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

package utils

import java.text.DecimalFormat
import java.time.format.DateTimeFormatter

import models.{CheckMode, UserAnswers, YearRange}
import pages.chargeB.ChargeBDetailsPage
import pages.chargeC.{IsSponsoringEmployerIndividualPage, SponsoringEmployerAddressPage, SponsoringIndividualDetailsPage, SponsoringOrganisationDetailsPage}
import pages.chargeE.{AnnualAllowanceYearPage, MemberDetailsPage, ChargeDetailsPage => ChargeEDetailsPage}
import pages.chargeD.{ChargeDetailsPage => ChargeDDetailsPage, MemberDetailsPage => ChargeDMemberDetailsPage}
import pages.chargeF.ChargeDetailsPage
import play.api.i18n.Messages
import uk.gov.hmrc.viewmodels.SummaryList._
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels._
import utils.CheckYourAnswersHelper._

class CheckYourAnswersHelper(userAnswers: UserAnswers, srn: String)(implicit messages: Messages) {

  def sponsoringIndividualDetails: Option[Row] = userAnswers.get(SponsoringIndividualDetailsPage) map {
    answer =>
      Row(
        key     = Key(msg"sponsoringIndividualDetails.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value   = Value(lit"$answer"),
        actions = List(
          Action(
            content            = msg"site.edit",
            href               = controllers.chargeC.routes.SponsoringIndividualDetailsController.onPageLoad(CheckMode, srn).url,
            visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"sponsoringIndividualDetails.checkYourAnswersLabel"))
          )
        )
      )
  }

  def sponsoringEmployerAddress: Option[Row] = userAnswers.get(SponsoringEmployerAddressPage) map {
    answer =>
      Row(
        key     = Key(msg"sponsoringEmployerAddress.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value   = Value(lit"$answer"),
        actions = List(
          Action(
            content            = msg"site.edit",
            href               = controllers.chargeC.routes.SponsoringEmployerAddressController.onPageLoad(CheckMode, srn).url,
            visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"sponsoringEmployerAddress.checkYourAnswersLabel"))
          )
        )
      )
  }

  def sponsoringOrganisationDetails: Option[Row] = userAnswers.get(SponsoringOrganisationDetailsPage) map {
    answer =>
      Row(
        key     = Key(msg"chargeC.sponsoringOrganisationDetails.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value   = Value(lit"$answer"),
        actions = List(
          Action(
            content            = msg"site.edit",
            href               = controllers.chargeC.routes.SponsoringOrganisationDetailsController.onPageLoad(CheckMode, srn).url,
            visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"chargeC.sponsoringOrganisationDetails.checkYourAnswersLabel"))
          )
        )
      )
  }

  def isSponsoringEmployerIndividual: Option[Row] = userAnswers.get(IsSponsoringEmployerIndividualPage) map {
    answer =>
      Row(
        key     = Key(msg"chargeC.isSponsoringEmployerIndividual.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value   = Value(yesOrNo(answer)),
        actions = List(
          Action(
            content            = msg"site.edit",
            href               = controllers.chargeC.routes.IsSponsoringEmployerIndividualController.onPageLoad(CheckMode, srn).url,
            visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"chargeC.isSponsoringEmployerIndividual.checkYourAnswersLabel"))
          )
        )
      )
  }

  def chargeFDate: Option[Row] = userAnswers.get(ChargeDetailsPage) map {
    answer =>
      Row(
        key = Key(msg"chargeF.chargeDetails.date.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.deRegistrationDate.format(dateFormatter)),classes = Seq("govuk-!-width-one-quarter")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeF.routes.ChargeDetailsController.onPageLoad(CheckMode, srn).url,
            visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"chargeF.chargeDetails.date.visuallyHidden.checkYourAnswersLabel"))
          )
        )
      )
  }

  def chargeFAmount: Option[Row] = userAnswers.get(pages.chargeF.ChargeDetailsPage) map {
    answer =>
      Row(
        key = Key(msg"chargeF.chargeDetails.amount.checkYourAnswersLabel",  classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(s"£${formatBigDecimalAsString(answer.amountTaxDue)}"),classes = Seq("govuk-!-width-one-quarter")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeF.routes.ChargeDetailsController.onPageLoad(CheckMode, srn).url,
            visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"chargeF.chargeDetails.amount.visuallyHidden.checkYourAnswersLabel"))
          )
        )
      )
  }

  def chargeAMembers: Option[Row] = userAnswers.get(pages.chargeA.ChargeDetailsPage) map {
    answer =>
      Row(
        key = Key(msg"chargeA.chargeDetails.numberOfMembers.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.numberOfMembers.toString),classes = Seq("govuk-!-width-one-quarter")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeA.routes.ChargeDetailsController.onPageLoad(CheckMode, srn).url,
            visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"chargeA.chargeDetails.numberOfMembers.visuallyHidden.checkYourAnswersLabel")
              )
          )
        )
      )
  }

  def chargeAAmountLowerRate: Option[Row] = userAnswers.get(pages.chargeA.ChargeDetailsPage) map {
    answer =>
      Row(
        key = Key(msg"chargeA.chargeDetails.amountLowerRate.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(s"£${formatBigDecimalAsString(answer.totalAmtOfTaxDueAtLowerRate)}"),classes = Seq("govuk-!-width-one-quarter")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeA.routes.ChargeDetailsController.onPageLoad(CheckMode, srn).url,
            visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"chargeA.chargeDetails.amountLowerRate.visuallyHidden.checkYourAnswersLabel"))
          )
        )
      )
  }

  def chargeAAmountHigherRate: Option[Row] = userAnswers.get(pages.chargeA.ChargeDetailsPage) map {
    answer =>
      Row(
        key = Key(msg"chargeA.chargeDetails.amountHigherRate.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(s"£${formatBigDecimalAsString(answer.totalAmtOfTaxDueAtHigherRate)}"),classes = Seq("govuk-!-width-one-quarter")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeA.routes.ChargeDetailsController.onPageLoad(CheckMode, srn).url,
            visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"chargeA.chargeDetails.amountHigherRate.visuallyHidden.checkYourAnswersLabel"))
          )
        )
      )
  }

  def total(total:BigDecimal) = Row(Key(msg"total", classes = Seq("govuk-!-width-one-half", "govuk-table__cell--numeric","govuk-!-font-weight-bold")),
    value = Value(Literal(s"£${formatBigDecimalAsString(total)}"))
  )

  def chargeBDetails: Option[Seq[Row]] = userAnswers.get(ChargeBDetailsPage) map {
    answer =>
      Seq(
        Row(
        key = Key(msg"chargeB.numberOfDeceased.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.numberOfDeceased.toString),classes = Seq("govuk-!-width-one-quarter")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeB.routes.ChargeDetailsController.onPageLoad(CheckMode, srn).url,
            visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"chargeB.numberOfDeceased.visuallyHidden.checkYourAnswersLabel"))
          )
        )
      ),
        Row(
          key = Key(msg"chargeB.totalTaxDue.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
          value = Value(Literal(s"£${formatBigDecimalAsString(answer.amountTaxDue)}"),classes = Seq("govuk-!-width-one-quarter")),
          actions = List(
            Action(
              content = msg"site.edit",
              href = controllers.chargeB.routes.ChargeDetailsController.onPageLoad(CheckMode, srn).url,
              visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"chargeB.totalTaxDue.visuallyHidden.checkYourAnswersLabel"))
            )
          )
        )
      )

  }

  def chargeEMemberDetails(index: Int): Option[Seq[Row]] = userAnswers.get(MemberDetailsPage(index)) map {
    answer =>
      Seq(
        Row(
          key = Key(msg"cya.memberName.label", classes = Seq("govuk-!-width-one-half")),
          value = Value(Literal(answer.fullName.toString),classes = Seq("govuk-!-width-one-quarter")),
          actions = List(
            Action(
              content = msg"site.edit",
              href = controllers.chargeE.routes.MemberDetailsController.onPageLoad(CheckMode, srn, index).url,
              visuallyHiddenText = Some(msg"visuallyHidden.memberName.label")
            )
          )
        ),
        Row(
          key = Key(msg"cya.nino.label".withArgs(answer.fullName), classes = Seq("govuk-!-width-one-half")),
          value = Value(Literal(answer.nino),classes = Seq("govuk-!-width-one-quarter")),
          actions = List(
            Action(
              content = msg"site.edit",
              href = controllers.chargeE.routes.MemberDetailsController.onPageLoad(CheckMode, srn, index).url,
              visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"cya.nino.label".withArgs(answer.fullName)))
            )
          )
        )
      )

  }

  def chargeETaxYear(index: Int): Option[Seq[Row]] = userAnswers.get(AnnualAllowanceYearPage(index)) map {
    answer =>
      Seq(
        Row(
          key = Key(msg"chargeE.cya.taxYear.label", classes = Seq("govuk-!-width-one-half")),
          value = Value(YearRange.getLabel(answer), classes = Seq("govuk-!-width-one-quarter")),
          actions = List(
            Action(
              content = msg"site.edit",
              href = controllers.chargeE.routes.AnnualAllowanceYearController.onPageLoad(CheckMode, srn, index).url,
              visuallyHiddenText = Some(msg"chargeE.visuallyHidden.taxYear.label")
            )
          )
        )
      )

  }


  def chargeEDetails(index: Int): Option[Seq[Row]] = userAnswers.get(ChargeEDetailsPage(index)) map {
    answer =>
      Seq(
        Row(
          key = Key(msg"chargeEDetails.chargeAmount.label", classes = Seq("govuk-!-width-one-half")),
          value = Value(Literal(s"£${formatBigDecimalAsString(answer.chargeAmount)}"), classes = Seq("govuk-!-width-one-quarter")),
          actions = List(
            Action(
              content = msg"site.edit",
              href = controllers.chargeE.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, index).url,
              visuallyHiddenText = Some(msg"chargeE.visuallyHidden.chargeAmount.label")
            )
          )
        ),
        Row(
          key = Key(msg"chargeEDetails.dateNoticeReceived.label", classes = Seq("govuk-!-width-one-half")),
          value = Value(Literal(answer.dateNoticeReceived.format(dateFormatter)), classes = Seq("govuk-!-width-one-quarter")),
          actions = List(
            Action(
              content = msg"site.edit",
              href = controllers.chargeE.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, index).url,
              visuallyHiddenText = Some(msg"chargeE.visuallyHidden.dateNoticeReceived.label")
            )
          )
        ),
        Row(
          key = Key(msg"chargeE.cya.mandatoryPayment.label", classes = Seq("govuk-!-width-one-half")),
          value = Value(yesOrNo(answer.isPaymentMandatory), classes = Seq("govuk-!-width-one-quarter")),
          actions = List(
            Action(
              content = msg"site.edit",
              href = controllers.chargeE.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, index).url,
              visuallyHiddenText = Some(msg"chargeE.visuallyHidden.isPaymentMandatory.label")
            )
          )
        )
      )
  }

  def chargeDMemberDetails(index: Int): Option[Seq[Row]] = userAnswers.get(ChargeDMemberDetailsPage(index)) map {
    answer =>
      Seq(
        Row(
          key = Key(msg"cya.memberName.label", classes = Seq("govuk-!-width-one-half")),
          value = Value(Literal(answer.fullName.toString),classes = Seq("govuk-!-width-one-quarter")),
          actions = List(
            Action(
              content = msg"site.edit",
              href = controllers.chargeD.routes.MemberDetailsController.onPageLoad(CheckMode, srn, index).url,
              visuallyHiddenText = Some(msg"visuallyHidden.memberName.label")
            )
          )
        ),
        Row(
          key = Key(msg"cya.nino.label".withArgs(answer.fullName), classes = Seq("govuk-!-width-one-half")),
          value = Value(Literal(answer.nino),classes = Seq("govuk-!-width-one-quarter")),
          actions = List(
            Action(
              content = msg"site.edit",
              href = controllers.chargeD.routes.MemberDetailsController.onPageLoad(CheckMode, srn, index).url,
              visuallyHiddenText = Some(msg"cya.nino.label".withArgs(answer.fullName))
            )
          )
        )
      )
  }

  def chargeDDetails(index: Int): Option[Seq[Row]] = userAnswers.get(ChargeDDetailsPage(index)) map {
    answer =>
      Seq(

        Row(
          key = Key(msg"chargeDDetails.dateOfEvent.label", classes = Seq("govuk-!-width-one-half")),
          value = Value(Literal(answer.dateOfEvent.format(dateFormatter)), classes = Seq("govuk-!-width-one-quarter")),
          actions = List(
            Action(
              content = msg"site.edit",
              href = controllers.chargeD.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, index).url,
              visuallyHiddenText = Some(msg"chargeDDetails.dateOfEvent.visuallyHidden.label")
            )
          )
        ),
        Row(
          key = Key(msg"taxAt25Percent.label", classes = Seq("govuk-!-width-one-half")),
          value = Value(Literal(s"£${formatBigDecimalAsString(answer.taxAt25Percent)}"), classes = Seq("govuk-!-width-one-quarter")),
          actions = List(
            Action(
              content = msg"site.edit",
              href = controllers.chargeD.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, index).url,
              visuallyHiddenText = Some(msg"taxAt25Percent.visuallyHidden.label")
            )
          )
        ),
        Row(
          key = Key(msg"taxAt55Percent.label", classes = Seq("govuk-!-width-one-half")),
          value = Value(Literal(s"£${formatBigDecimalAsString(answer.taxAt55Percent)}"), classes = Seq("govuk-!-width-one-quarter")),
          actions = List(
            Action(
              content = msg"site.edit",
              href = controllers.chargeD.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, index).url,
              visuallyHiddenText = Some(msg"taxAt55Percent.visuallyHidden.label")
            )
          )
        )
      )
  }

  private def yesOrNo(answer: Boolean): Content =
    if (answer) {
      msg"site.yes"
    } else {
      msg"site.no"
    }
}

object CheckYourAnswersHelper {
  private val decimalFormat = new DecimalFormat("0.00")
  private val dateFormatter = DateTimeFormatter.ofPattern("d/M/yyyy")

  def formatBigDecimalAsString(bd:BigDecimal):String = decimalFormat.format(bd)
}
