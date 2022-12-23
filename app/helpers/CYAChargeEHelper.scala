/*
 * Copyright 2022 HM Revenue & Customs
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
import models.mccloud.{PensionsRemedySchemeSummary, PensionsRemedySummary}
import models.{AFTQuarter, AccessType, CheckMode, YearRange}
import play.api.i18n.Messages
import uk.gov.hmrc.viewmodels.SummaryList.{Action, Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels._

import java.time.LocalDate

class CYAChargeEHelper(srn: String, startDate: LocalDate, accessType: AccessType, version: Int)(implicit messages: Messages) extends CYAHelper {

  def chargeEMemberDetails(index: Int, answer: models.MemberDetails): Seq[Row] = {
    Seq(
      Row(
        key = Key(msg"cya.memberName.label", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.fullName.toString), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = Html(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
            href = controllers.chargeE.routes.MemberDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
            visuallyHiddenText = Some(Literal(
              messages("site.edit") + " " + messages("visuallyHidden.memberName.label")
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
            href = controllers.chargeE.routes.MemberDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
            visuallyHiddenText = Some(Literal(
              messages("site.edit") + " " + messages("cya.nino.label", answer.fullName)
            ))
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
            content = Html(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
            href = controllers.chargeE.routes.AnnualAllowanceYearController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
            visuallyHiddenText = Some(Literal(
              messages("site.edit") + " " + messages("chargeE.visuallyHidden.taxYear.label")
            ))
          )
        )
      )
    )
  }


  def chargeEDetails(index: Int, answer: ChargeEDetails): Seq[Row] = {
    Seq(
      Row(
        key = Key(msg"chargeEDetails.chargeAmount.label", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(answer.chargeAmount)}"), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = Html(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
            href = controllers.chargeE.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
            visuallyHiddenText = Some(Literal(
              messages("site.edit") + " " + messages("chargeE.visuallyHidden.chargeAmount.label")
            ))
          )
        )
      ),
      Row(
        key = Key(msg"chargeEDetails.dateNoticeReceived.label", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.dateNoticeReceived.format(FormatHelper.dateFormatter)), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = Html(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
            href = controllers.chargeE.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
            visuallyHiddenText = Some(Literal(
              messages("site.edit") + " " + messages("chargeE.visuallyHidden.dateNoticeReceived.label")
            ))
          )
        )
      ),
      Row(
        key = Key(msg"chargeE.cya.mandatoryPayment.label", classes = Seq("govuk-!-width-one-half")),
        value = Value(yesOrNo(answer.isPaymentMandatory), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = Html(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
            href = controllers.chargeE.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
            visuallyHiddenText = Some(Literal(
              messages("site.edit") + " " + messages("chargeE.visuallyHidden.isPaymentMandatory.label")
            ))
          )
        )
      )
    )
  }

  private def getOptionalValue(v: Option[Boolean]): Content = {
    v match {
      case None => msg"mccloud.not.entered"
      case Some(b) => yesOrNo(b)
    }
  }

  def publicServicePensionsRemedyEDetails(index: Int, pensionsRemedySummary: PensionsRemedySummary): Seq[Row] = {
    val chargeTypeDescription = Messages(s"chargeType.description.${ChargeTypeAnnualAllowance}")

    val pensionsRemedySeq =
      Seq(
        Row(
          key = Key(msg"${messages("isPublicServicePensionsRemedy.title", chargeTypeDescription)}", classes = Seq("govuk-!-width-one-half")),
          value = Value(getOptionalValue(pensionsRemedySummary.isPublicServicePensionsRemedy), classes = Seq("govuk-!-width-one-third")),
          actions = List(
            Action(
              content = Html(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.mccloud.routes.IsPublicServicePensionsRemedyController
                .onPageLoad(ChargeTypeAnnualAllowance, CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(Literal(
                messages("site.edit") + " " + messages("isPublicServicePensionsRemedy.label")
              ))
            )
          )
        ),
        Row(
          key = Key(msg"${messages("isChargeInAdditionReported.title", chargeTypeDescription)}", classes = Seq("govuk-!-width-one-half")),
          value = Value(getOptionalValue(pensionsRemedySummary.isChargeInAdditionReported), classes = Seq("govuk-!-width-one-third")),
          actions = List(
            Action(
              content = Html(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.mccloud.routes.IsChargeInAdditionReportedController
                .onPageLoad(ChargeTypeAnnualAllowance, CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(Literal(
                messages("site.edit") + " " + messages("isChargeInAdditionReported.label")
              ))
            )
          )
        ),
        Row(
          key = Key(msg"${messages("wasAnotherPensionScheme.title", chargeTypeDescription)}", classes = Seq("govuk-!-width-one-half")),
          value = Value(getOptionalValue(pensionsRemedySummary.wasAnotherPensionScheme), classes = Seq("govuk-!-width-one-third")),
          actions = List(
            Action(
              content = Html(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.mccloud.routes.WasAnotherPensionSchemeController
                .onPageLoad(ChargeTypeAnnualAllowance, CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(Literal(
                messages("site.edit") + " " + messages("wasAnotherPensionScheme.label")
              ))
            )
          )
        )
      )

    val pensionSchemeRows = for(pensionsRemedySchemeSummary <- pensionsRemedySummary.pensionsRemedySchemeSummary)
      yield pensionsRemedySchemeSummaryDetails(index, pensionsRemedySchemeSummary)

    Seq(pensionsRemedySeq, pensionSchemeRows.flatten).flatten
  }

  private def getOptionalYearValue(v: Option[YearRange]): Content = {
    v match {
      case None => msg"mccloud.not.entered"
      case Some(b) =>  YearRange.getLabel(b)
    }
  }

  private def getOptionalLiteralValue(v: Option[String]): Content = {
    v match {
      case None => msg"mccloud.not.entered"
      case Some(b) => Literal(b)
    }
  }

  private def getOptionalLiteralQQQValue(v: Option[AFTQuarter]): Content = {
    v match {
      case None => msg"mccloud.not.entered"
      case Some(b) => Literal(AFTQuarter.formatForDisplay(b))
    }
  }


  def pensionsRemedySchemeSummaryDetails(index: Int, pensionsRemedySchemeSummary: PensionsRemedySchemeSummary): Seq[Row] = {
    val chargeTypeDescription = Messages(s"chargeType.description.${ChargeTypeAnnualAllowance}")

    Seq(
      Row(
        key = Key(msg"${messages("pensionsRemedySchemeSummary.pstrNumber.title", chargeTypeDescription)}", classes = Seq("govuk-!-width-one-half")),
        value = Value(getOptionalLiteralValue(pensionsRemedySchemeSummary.pstrNumber), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = Html(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
            href = controllers.mccloud.routes.IsChargeInAdditionReportedController
              .onPageLoad(ChargeTypeAnnualAllowance, CheckMode, srn, startDate, accessType, version, index).url,
            visuallyHiddenText = Some(Literal(
              messages("site.edit") + " " + messages("isChargeInAdditionReported.label")
            ))
          )
        )
      ),
      Row(
        key = Key(msg"${messages("pensionsRemedySchemeSummary.taxYear", chargeTypeDescription)}", classes = Seq("govuk-!-width-one-half")),
        value = Value(getOptionalYearValue(pensionsRemedySchemeSummary.taxYear), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = Html(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
            href = controllers.mccloud.routes.IsPublicServicePensionsRemedyController
              .onPageLoad(ChargeTypeAnnualAllowance, CheckMode, srn, startDate, accessType, version, index).url,
            visuallyHiddenText = Some(Literal(
              messages("site.edit") + " " + messages("pensionsRemedySchemeSummary.taxYear.label")
            ))
          )
        )
      ),
      Row(
        key = Key(msg"${messages("wasAnotherPensionScheme.title", chargeTypeDescription)}", classes = Seq("govuk-!-width-one-half")),
        value = Value(getOptionalLiteralQQQValue(pensionsRemedySchemeSummary.taxQuarter), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = Html(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
            href = controllers.mccloud.routes.WasAnotherPensionSchemeController
              .onPageLoad(ChargeTypeAnnualAllowance, CheckMode, srn, startDate, accessType, version, index).url,
            visuallyHiddenText = Some(Literal(
              messages("site.edit") + " " + messages("wasAnotherPensionScheme.label")
            ))
          )
        )
      ),
      Row(
        key = Key(msg"${messages("wasAnotherPensionScheme.title", chargeTypeDescription)}", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(s"${
          FormatHelper.formatCurrencyAmountAsString(
            pensionsRemedySchemeSummary.chargeAmountReported.getOrElse(BigDecimal(0.00)))
        }"), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = Html(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
            href = controllers.mccloud.routes.WasAnotherPensionSchemeController
              .onPageLoad(ChargeTypeAnnualAllowance, CheckMode, srn, startDate, accessType, version, index).url,
            visuallyHiddenText = Some(Literal(
              messages("site.edit") + " " + messages("wasAnotherPensionScheme.label")
            ))
          )
        )
      )
    )
  }

}
