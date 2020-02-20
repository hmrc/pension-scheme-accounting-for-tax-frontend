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

import models.chargeB.ChargeBDetails
import models.chargeC.{ChargeCDetails, SponsoringEmployerAddress, SponsoringOrganisationDetails}
import models.chargeD.ChargeDDetails
import models.chargeE.ChargeEDetails
import models.chargeF.ChargeDetails
import models.chargeG.{ChargeAmounts, MemberDetails}
import models.{CheckMode, UserAnswers, YearRange}
import pages.ConfirmSubmitAFTReturnPage
import pages.chargeC._
import play.api.i18n.Messages
import uk.gov.hmrc.viewmodels.SummaryList._
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels._
import utils.CheckYourAnswersHelper._

case object DataMissingException extends Exception

class CheckYourAnswersHelper(userAnswers: UserAnswers, srn: String)(implicit messages: Messages) {

  private def addressAnswer(addr: SponsoringEmployerAddress)(implicit messages: Messages): Html = {
    def addrLineToHtml(l: String): String = s"""<span class="govuk-!-display-block">$l</span>"""

    def optionalAddrLineToHtml(optionalAddrLine: Option[String]): String = optionalAddrLine match {
      case Some(l) => addrLineToHtml(l)
      case None => ""
    }

    Html(
      addrLineToHtml(addr.line1) +
        addrLineToHtml(addr.line2) +
        optionalAddrLineToHtml(addr.line3) +
        optionalAddrLineToHtml(addr.line4) +
        optionalAddrLineToHtml(addr.postcode) +
        addrLineToHtml(messages("country." + addr.country))
    )
  }

  def chargeCEmployerDetails(index: Int,
                             sponsorDetails: Either[models.MemberDetails, SponsoringOrganisationDetails]
                            )(implicit messages: Messages): Seq[Row] =
    sponsorDetails.fold(
      individual => chargeCIndividualDetails(index, individual),
      organisation => chargeCOrganisationDetails(index, organisation)
    )

  private def getEmployerName(index: Int,
                              sponsorDetails: Either[models.MemberDetails, SponsoringOrganisationDetails]): String =
    sponsorDetails.fold(
      individual => individual.fullName,
      organisation => organisation.name
    )

  def chargeCIsSponsoringEmployerIndividual(index: Int, answer: Boolean): Row =
    Row(
      key = Key(msg"chargeC.isSponsoringEmployerIndividual.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
      value = Value(yesOrNo(answer)),
      actions = List(
        Action(
          content = msg"site.edit",
          href = controllers.chargeC.routes.IsSponsoringEmployerIndividualController.onPageLoad(CheckMode, srn, index).url,
          visuallyHiddenText = Some(msg"chargeC.isSponsoringEmployerIndividual.visuallyHidden.checkYourAnswersLabel"))
      )
    )

  def chargeCIndividualDetails(index: Int, answer: models.MemberDetails): Seq[Row] = {
    Seq(
      Row(
        key = Key(msg"chargeC.sponsoringIndividualName.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(lit"${answer.fullName}"),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeC.routes.SponsoringIndividualDetailsController.onPageLoad(CheckMode, srn, index).url,
            visuallyHiddenText = Some(msg"chargeC.sponsoringIndividualName.visuallyHidden.checkYourAnswersLabel")
          )
        )
      ),
      Row(
        key = Key(msg"chargeC.sponsoringIndividualNino.checkYourAnswersLabel".withArgs(answer.fullName), classes = Seq("govuk-!-width-one-half")),
        value = Value(lit"${answer.nino}"),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeC.routes.SponsoringIndividualDetailsController.onPageLoad(CheckMode, srn, index).url,
            visuallyHiddenText = Some(msg"chargeC.sponsoringIndividualNino.visuallyHidden.checkYourAnswersLabel".withArgs(answer.fullName))
          )
        )
      )
    )
  }

  def chargeCOrganisationDetails(index: Int, answer: SponsoringOrganisationDetails): Seq[Row] = {
    Seq(
      Row(
        key = Key(msg"chargeC.sponsoringOrganisationName.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(lit"${answer.name}"),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeC.routes.SponsoringOrganisationDetailsController.onPageLoad(CheckMode, srn, index).url,
            visuallyHiddenText = Some(msg"chargeC.sponsoringOrganisationName.visuallyHidden.checkYourAnswersLabel")
          )
        )
      ),
      Row(
        key = Key(msg"chargeC.sponsoringOrganisationCrn.checkYourAnswersLabel".withArgs(answer.name), classes = Seq("govuk-!-width-one-half")),
        value = Value(lit"${answer.crn}"),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeC.routes.SponsoringOrganisationDetailsController.onPageLoad(CheckMode, srn, index).url,
            visuallyHiddenText = Some(msg"chargeC.sponsoringOrganisationCrn.visuallyHidden.checkYourAnswersLabel".withArgs(answer.name))
          )
        )
      )
    )
  }

  def chargeCAddress(index: Int,
                     address: SponsoringEmployerAddress,
                     sponsorDetails: Either[models.MemberDetails, SponsoringOrganisationDetails])
                    (implicit messages: Messages): Row =
    Row(
      key = Key(msg"chargeC.sponsoringEmployerAddress.checkYourAnswersLabel".withArgs(getEmployerName(index, sponsorDetails)), classes = Seq("govuk-!-width-one-half")),
      value = Value(addressAnswer(address)),
      actions = List(
        Action(
          content = msg"site.edit",
          href = controllers.chargeC.routes.SponsoringEmployerAddressController.onPageLoad(CheckMode, srn, index).url,
          visuallyHiddenText = Some(msg"chargeC.sponsoringEmployerAddress.checkYourAnswersLabel".withArgs(getEmployerName(index, sponsorDetails)))
        )
      )
    )

  def chargeCChargeDetails(index: Int, chargeDetails: ChargeCDetails): Seq[Row] =
    Seq(
      Row(
        key = Key(msg"chargeC.paymentDate.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(chargeDetails.paymentDate.format(dateFormatter)), classes = Seq("govuk-!-width-one-quarter")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeC.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, index).url,
            visuallyHiddenText = Some(msg"chargeC.paymentDate.visuallyHidden.checkYourAnswersLabel")
          )
        )
      ),
      Row(
        key = Key(msg"chargeC.totalTaxDue.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(s"£${formatBigDecimalAsString(chargeDetails.amountTaxDue)}"), classes = Seq("govuk-!-width-one-quarter")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeC.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, index).url,
            visuallyHiddenText = Some(msg"chargeC.totalTaxDue.visuallyHidden.checkYourAnswersLabel")
          )
        )
      )
    )

  def chargeFDate(answer: ChargeDetails): Row =
    Row(
      key = Key(msg"chargeF.chargeDetails.date.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
      value = Value(Literal(answer.deRegistrationDate.format(dateFormatter)), classes = Seq("govuk-!-width-one-quarter")),
      actions = List(
        Action(
          content = msg"site.edit",
          href = controllers.chargeF.routes.ChargeDetailsController.onPageLoad(CheckMode, srn).url,
          visuallyHiddenText = Some(msg"chargeF.chargeDetails.date.visuallyHidden.checkYourAnswersLabel")
        )
      )
    )


  def chargeFAmount(answer: ChargeDetails): Row =
    Row(
      key = Key(msg"chargeF.chargeDetails.amount.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
      value = Value(Literal(s"£${formatBigDecimalAsString(answer.amountTaxDue)}"), classes = Seq("govuk-!-width-one-quarter")),
      actions = List(
        Action(
          content = msg"site.edit",
          href = controllers.chargeF.routes.ChargeDetailsController.onPageLoad(CheckMode, srn).url,
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
          href = controllers.chargeA.routes.ChargeDetailsController.onPageLoad(CheckMode, srn).url,
          visuallyHiddenText = Some(msg"chargeA.chargeDetails.numberOfMembers.visuallyHidden.checkYourAnswersLabel")
        )
      )
    )
  }

  def chargeAAmountLowerRate(answer: models.chargeA.ChargeDetails): Row = {
    Row(
      key = Key(msg"chargeA.chargeDetails.amountLowerRate.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
      value = Value(Literal(s"£${formatBigDecimalAsString(answer.totalAmtOfTaxDueAtLowerRate.getOrElse(BigDecimal(0.00)))}"), classes = Seq("govuk-!-width-one-quarter")),
      actions = List(
        Action(
          content = msg"site.edit",
          href = controllers.chargeA.routes.ChargeDetailsController.onPageLoad(CheckMode, srn).url,
          visuallyHiddenText = Some(msg"chargeA.chargeDetails.amountLowerRate.visuallyHidden.checkYourAnswersLabel")
        )
      )
    )
  }

  def chargeAAmountHigherRate(answer: models.chargeA.ChargeDetails): Row = {
    Row(
      key = Key(msg"chargeA.chargeDetails.amountHigherRate.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
      value = Value(Literal(s"£${formatBigDecimalAsString(answer.totalAmtOfTaxDueAtHigherRate.getOrElse(BigDecimal(0.00)))}"), classes = Seq("govuk-!-width-one-quarter")),
      actions = List(
        Action(
          content = msg"site.edit",
          href = controllers.chargeA.routes.ChargeDetailsController.onPageLoad(CheckMode, srn).url,
          visuallyHiddenText = Some(msg"chargeA.chargeDetails.amountHigherRate.visuallyHidden.checkYourAnswersLabel")
        )
      )
    )
  }

  def total(total: BigDecimal): Row = Row(
    key = Key(msg"total", classes = Seq("govuk-!-width-one-half", "govuk-table__cell--numeric", "govuk-!-font-weight-bold")),
    value = Value(Literal(s"£${formatBigDecimalAsString(total)}"))
  )

  def chargeBDetails(answer: ChargeBDetails): Seq[Row] = {
    Seq(
      Row(
        key = Key(msg"chargeB.numberOfDeceased.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.numberOfDeceased.toString), classes = Seq("govuk-!-width-one-quarter")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeB.routes.ChargeDetailsController.onPageLoad(CheckMode, srn).url,
            visuallyHiddenText = Some(msg"chargeB.numberOfDeceased.visuallyHidden.checkYourAnswersLabel")
          )
        )
      ),
      Row(
        key = Key(msg"chargeB.totalTaxDue.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(s"£${formatBigDecimalAsString(answer.amountTaxDue)}"), classes = Seq("govuk-!-width-one-quarter")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeB.routes.ChargeDetailsController.onPageLoad(CheckMode, srn).url,
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
            href = controllers.chargeE.routes.MemberDetailsController.onPageLoad(CheckMode, srn, index).url,
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
            href = controllers.chargeE.routes.MemberDetailsController.onPageLoad(CheckMode, srn, index).url,
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
            href = controllers.chargeE.routes.AnnualAllowanceYearController.onPageLoad(CheckMode, srn, index).url,
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
        value = Value(Literal(s"£${formatBigDecimalAsString(answer.chargeAmount)}"), classes = Seq("govuk-!-width-one-third")),
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
        value = Value(Literal(answer.dateNoticeReceived.format(dateFormatter)), classes = Seq("govuk-!-width-one-third")),
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
        value = Value(yesOrNo(answer.isPaymentMandatory), classes = Seq("govuk-!-width-one-third")),
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

  def chargeDMemberDetails(index: Int, answer: models.MemberDetails): Seq[Row] = {
    Seq(
      Row(
        key = Key(msg"cya.memberName.label", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.fullName.toString), classes = Seq("govuk-!-width-one-third")),
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
        value = Value(Literal(answer.nino), classes = Seq("govuk-!-width-one-third")),
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

  def chargeDDetails(index: Int, answer: ChargeDDetails): Seq[Row] = {
    Seq(
      Row(
        key = Key(msg"chargeDDetails.dateOfEvent.label", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.dateOfEvent.format(dateFormatter)), classes = Seq("govuk-!-width-one-third")),
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
        value = Value(Literal(s"£${formatBigDecimalAsString(answer.taxAt25Percent.getOrElse(BigDecimal(0.00)))}"), classes = Seq("govuk-!-width-one-third")),
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
        value = Value(Literal(s"£${formatBigDecimalAsString(answer.taxAt55Percent.getOrElse(BigDecimal(0.00)))}"), classes = Seq("govuk-!-width-one-third")),
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

  def chargeGMemberDetails(index: Int, answer: MemberDetails): Seq[Row] = {
    Seq(
      Row(
        key = Key(msg"cya.memberName.label", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.fullName.toString), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeG.routes.MemberDetailsController.onPageLoad(CheckMode, srn, index).url,
            visuallyHiddenText = Some(msg"visuallyHidden.memberName.label")
          )
        )
      ),
      Row(
        key = Key(msg"dob.cya.label".withArgs(answer.fullName), classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.dob.format(dateFormatter)), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeG.routes.MemberDetailsController.onPageLoad(CheckMode, srn, index).url,
            visuallyHiddenText = Some(msg"dob.cya.label".withArgs(answer.fullName))
          )
        )
      ),
      Row(
        key = Key(msg"cya.nino.label".withArgs(answer.fullName), classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.nino), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeG.routes.MemberDetailsController.onPageLoad(CheckMode, srn, index).url,
            visuallyHiddenText = Some(msg"cya.nino.label".withArgs(answer.fullName))
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
            content = msg"site.edit",
            href = controllers.chargeG.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, index).url,
            visuallyHiddenText = Some(msg"chargeGDetails.qropsReferenceNumber.visuallyHidden.label")
          )
        )
      ),
      Row(
        key = Key(msg"chargeG.chargeDetails.qropsTransferDate.label", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.qropsTransferDate.format(dateFormatter)), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeG.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, index).url,
            visuallyHiddenText = Some(msg"chargeGDetails.qropsTransferDate.visuallyHidden.label")
          )
        )
      )
    )
  }

  def chargeGAmounts(index: Int, answer: ChargeAmounts): Seq[Row] = {
    Seq(
      Row(
        key = Key(msg"chargeG.chargeAmount.transferred", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(s"£${formatBigDecimalAsString(answer.amountTransferred)}"), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeG.routes.ChargeAmountsController.onPageLoad(CheckMode, srn, index).url,
            visuallyHiddenText = Some(msg"chargeG.chargeAmount.transferred.visuallyHidden.label")
          )
        )
      ),
      Row(
        key = Key(msg"chargeG.chargeAmount.taxDue", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(s"£${formatBigDecimalAsString(answer.amountTaxDue)}"), classes = Seq("govuk-!-width-one-thirdt run" +
          "")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeG.routes.ChargeAmountsController.onPageLoad(CheckMode, srn, index).url,
            visuallyHiddenText = Some(msg"chargeG.chargeAmount.taxDue.visuallyHidden.label")
          )
        )
      )
    )
  }

  private def yesOrNo(answer: Boolean): Content =
    if (answer) msg"site.yes" else msg"site.no"

  def rows(viewOnly: Boolean, rows: Seq[SummaryList.Row]): Seq[SummaryList.Row] = {
    if(viewOnly) rows.map(_.copy(actions = Nil)) else rows
  }
}

object CheckYourAnswersHelper {
  private val decimalFormat = new DecimalFormat("0.00")
  private val dateFormatter = DateTimeFormatter.ofPattern("d/M/yyyy")

  def formatBigDecimalAsString(bd: BigDecimal): String = decimalFormat.format(bd)
}
