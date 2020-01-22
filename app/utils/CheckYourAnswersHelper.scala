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

import models.chargeC.{ChargeCDetails, SponsoringEmployerAddress, SponsoringOrganisationDetails}
import models.{CheckMode, MemberDetails, UserAnswers, YearRange}
import pages.chargeB.ChargeBDetailsPage
import pages.chargeC._
import pages.chargeD.{ChargeDetailsPage => ChargeDDetailsPage, MemberDetailsPage => ChargeDMemberDetailsPage}
import pages.chargeE.{AnnualAllowanceYearPage, MemberDetailsPage, ChargeDetailsPage => ChargeEDetailsPage}
import pages.chargeF.ChargeDetailsPage
import pages.chargeG.{ChargeAmountsPage, ChargeDetailsPage => ChargeGDetailsPage, MemberDetailsPage => ChargeGMemberDetailsPage}
import play.api.i18n.Messages
import uk.gov.hmrc.viewmodels.SummaryList._
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels._
import utils.CheckYourAnswersHelper._

class CheckYourAnswersHelper(userAnswers: UserAnswers, srn: String)(implicit messages: Messages) {

  private def addressAnswer(addr: SponsoringEmployerAddress)(implicit messages: Messages): Html = {
    def addrLineToHtml(l: String): String = s"""<span class="govuk-!-display-block">$l</span>"""

    def optionalAddrLineToHtml(optionalAddrLine: Option[String]): String = optionalAddrLine match {
      case None => ""
      case Some(l) => addrLineToHtml(l)
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

  def chargeCEmployerDetails(index: Int)(implicit messages: Messages): Seq[Row] =
    userAnswers.get(IsSponsoringEmployerIndividualPage(index)) match {
      case Some(true) => chargeCIndividualDetails(index).get
      case Some(false) => chargeCOrganisationDetails(index).get
      case _ => Seq.empty
    }

  private def getEmployerName(index: Int): String =
    (userAnswers.get(IsSponsoringEmployerIndividualPage(index)),
      userAnswers.get(SponsoringIndividualDetailsPage(index)),
      userAnswers.get(SponsoringOrganisationDetailsPage(index))
    ) match {
      case (Some(true), Some(individualDetails), _) => individualDetails.fullName
      case (Some(false), _, Some(organisationDetails)) => organisationDetails.name
    }

  def chargeCIsSponsoringEmployerIndividual(index: Int): Option[Row] =
    userAnswers.get(IsSponsoringEmployerIndividualPage(index)) map { answer =>
      Row(
        key = Key(msg"chargeC.isSponsoringEmployerIndividual.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(yesOrNo(answer)),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeC.routes.IsSponsoringEmployerIndividualController.onPageLoad(CheckMode, srn, index).url,
            visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"chargeC.isSponsoringEmployerIndividual.checkYourAnswersLabel"))
          )
        )
      )
    }

  def chargeCIndividualDetails(index: Int): Option[Seq[Row]] =
    userAnswers.get(SponsoringIndividualDetailsPage(index)) map { individualDetails =>
      Seq(
        Row(
        key = Key(msg"chargeC.sponsoringIndividualName.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(lit"${individualDetails.fullName}"),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeC.routes.SponsoringIndividualDetailsController.onPageLoad(CheckMode, srn, index).url,
            visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"chargeC.sponsoringIndividualName.visuallyHidden.checkYourAnswersLabel"))
          )
        )
      ),
      Row(
        key = Key(msg"chargeC.sponsoringIndividualNino.checkYourAnswersLabel".withArgs(individualDetails.fullName), classes = Seq("govuk-!-width-one-half")),
        value = Value(lit"${individualDetails.nino}"),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeC.routes.SponsoringIndividualDetailsController.onPageLoad(CheckMode, srn, index).url,
            visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"chargeC.sponsoringIndividualNino.visuallyHidden.checkYourAnswersLabel"))
          )
        )
      )
    )
  }

  def chargeCOrganisationDetails(index: Int): Option[Seq[Row]] =
    userAnswers.get(SponsoringOrganisationDetailsPage(index)) map { organisationDetails =>
      Seq(
        Row(
          key = Key(msg"chargeC.sponsoringOrganisationName.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
          value = Value(lit"${organisationDetails.name}"),
          actions = List(
            Action(
              content = msg"site.edit",
              href = controllers.chargeC.routes.SponsoringOrganisationDetailsController.onPageLoad(CheckMode, srn, index).url,
              visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"chargeC.sponsoringOrganisationName.visuallyHidden.checkYourAnswersLabel"))
            )
          )
        ),
        Row(
          key = Key(msg"chargeC.sponsoringOrganisationCrn.checkYourAnswersLabel".withArgs(organisationDetails.name), classes = Seq("govuk-!-width-one-half")),
          value = Value(lit"${organisationDetails.crn}"),
          actions = List(
            Action(
              content = msg"site.edit",
              href = controllers.chargeC.routes.SponsoringOrganisationDetailsController.onPageLoad(CheckMode, srn, index).url,
              visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"chargeC.sponsoringOrganisationCrn.visuallyHidden.checkYourAnswersLabel"))
            )
          )
        )
      )
    }

  def chargeCAddress(index: Int)(implicit messages: Messages): Option[Row] =
   userAnswers.get(SponsoringEmployerAddressPage(index)) map { addr =>
    Row(
      key = Key(msg"chargeC.sponsoringEmployerAddress.checkYourAnswersLabel".withArgs(getEmployerName(index)), classes = Seq("govuk-!-width-one-half")),
      value = Value(addressAnswer(addr)),
      actions = List(
        Action(
          content = msg"site.edit",
          href = controllers.chargeC.routes.SponsoringEmployerAddressController.onPageLoad(CheckMode, srn, index).url,
          visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"chargeC.sponsoringEmployerAddress.checkYourAnswersLabel"))
        )
      )
    )
  }

  def chargeCChargeDetails(index: Int): Option[Seq[Row]] =
    userAnswers.get(ChargeCDetailsPage(index)) map { chargeDetails =>
      Seq(
        Row(
          key = Key(msg"chargeC.paymentDate.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
          value = Value(Literal(chargeDetails.paymentDate.format(dateFormatter)), classes = Seq("govuk-!-width-one-quarter")),
          actions = List(
            Action(
              content = msg"site.edit",
              href = controllers.chargeC.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, index).url,
              visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"chargeC.paymentDate.visuallyHidden.checkYourAnswersLabel"))
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
              visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"chargeC.totalTaxDue.visuallyHidden.checkYourAnswersLabel"))
            )
          )
        )
      )
    }

  def chargeGDate(index: Int): Option[Row] = userAnswers.get(pages.chargeG.ChargeDetailsPage(index)) map {
    answer =>
      Row(
        key     = Key(msg"chargeG.chargeDetails.qropsTransferDate.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value   = Value(Literal(answer.qropsTransferDate.format(dateFormatter))),
        actions = List(
          Action(
            content            = msg"site.edit",
            href               = controllers.chargeG.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, index).url,
            visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"chargeG.chargeDetails.qropsTransferDate.visuallyHidden.checkYourAnswersLabel"))
          )
        )
      )
  }

  def chargeGQROPSReferenceNumber(index: Int): Option[Row] = userAnswers.get(pages.chargeG.ChargeDetailsPage(index)) map {
    answer =>
      Row(
        key     = Key(msg"chargeG.chargeDetails.GQROPSReferenceNumber.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value   = Value(Literal(answer.qropsReferenceNumber)),
        actions = List(
          Action(
            content            = msg"site.edit",
            href               = controllers.chargeG.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, index).url,
            visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"chargeG.chargeDetails.qropsReferenceNumber.visuallyHidden.checkYourAnswersLabel"))
          )
        )
      )
  }

  def chargeFDate: Option[Row] = userAnswers.get(ChargeDetailsPage) map {
    answer =>
      Row(
        key = Key(msg"chargeF.chargeDetails.date.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.deRegistrationDate.format(dateFormatter)), classes = Seq("govuk-!-width-one-quarter")),
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
        key = Key(msg"chargeF.chargeDetails.amount.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(s"£${formatBigDecimalAsString(answer.amountTaxDue)}"), classes = Seq("govuk-!-width-one-quarter")),
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
        value = Value(Literal(answer.numberOfMembers.toString), classes = Seq("govuk-!-width-one-quarter")),
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
        value = Value(Literal(s"£${formatBigDecimalAsString(answer.totalAmtOfTaxDueAtLowerRate.getOrElse(BigDecimal(0.00)))}"), classes = Seq("govuk-!-width-one-quarter")),
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
        value = Value(Literal(s"£${formatBigDecimalAsString(answer.totalAmtOfTaxDueAtHigherRate.getOrElse(BigDecimal(0.00)))}"), classes = Seq("govuk-!-width-one-quarter")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeA.routes.ChargeDetailsController.onPageLoad(CheckMode, srn).url,
            visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"chargeA.chargeDetails.amountHigherRate.visuallyHidden.checkYourAnswersLabel"))
          )
        )
      )
  }

  def total(total: BigDecimal): Row = Row(
    key = Key(msg"total", classes = Seq("govuk-!-width-one-half", "govuk-table__cell--numeric", "govuk-!-font-weight-bold")),
    value = Value(Literal(s"£${formatBigDecimalAsString(total)}"))
  )

  def chargeBDetails: Option[Seq[Row]] = userAnswers.get(ChargeBDetailsPage) map {
    answer =>
      Seq(
        Row(
          key = Key(msg"chargeB.numberOfDeceased.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
          value = Value(Literal(answer.numberOfDeceased.toString), classes = Seq("govuk-!-width-one-quarter")),
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
          value = Value(Literal(s"£${formatBigDecimalAsString(answer.amountTaxDue)}"), classes = Seq("govuk-!-width-one-quarter")),
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
          value = Value(Literal(answer.fullName.toString),classes = Seq("govuk-!-width-one-third")),
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
          value = Value(Literal(answer.nino),classes = Seq("govuk-!-width-one-third")),
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


  def chargeEDetails(index: Int): Option[Seq[Row]] = userAnswers.get(ChargeEDetailsPage(index)) map {
    answer =>
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

  def chargeDMemberDetails(index: Int): Option[Seq[Row]] = userAnswers.get(ChargeDMemberDetailsPage(index)) map {
    answer =>
      Seq(
        Row(
          key = Key(msg"cya.memberName.label", classes = Seq("govuk-!-width-one-half")),
          value = Value(Literal(answer.fullName.toString),classes = Seq("govuk-!-width-one-third")),
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
          value = Value(Literal(answer.nino),classes = Seq("govuk-!-width-one-third")),
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

  def chargeGMemberDetails(index: Int): Option[Seq[Row]] = userAnswers.get(ChargeGMemberDetailsPage(index)) map {
    answer =>
      Seq(
        Row(
          key = Key(msg"cya.memberName.label", classes = Seq("govuk-!-width-one-half")),
          value = Value(Literal(answer.fullName.toString),classes = Seq("govuk-!-width-one-third")),
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
          value = Value(Literal(answer.nino),classes = Seq("govuk-!-width-one-third")),
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

  def chargeGDetails(index: Int): Option[Seq[Row]] = userAnswers.get(ChargeGDetailsPage(index)) map {
    answer =>
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

  def chargeGAmounts(index: Int): Option[Seq[Row]] = userAnswers.get(ChargeAmountsPage(index)) map {
    answer =>
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
    if (answer) {
      msg"site.yes"
    } else {
      msg"site.no"
    }
}

object CheckYourAnswersHelper {
  private val decimalFormat = new DecimalFormat("0.00")
  private val dateFormatter = DateTimeFormatter.ofPattern("d/M/yyyy")

  def formatBigDecimalAsString(bd: BigDecimal): String = decimalFormat.format(bd)
}