/*
 * Copyright 2023 HM Revenue & Customs
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
import models.mccloud.{PensionsRemedySchemeSummary, PensionsRemedySummary}
import models.{AFTQuarter, AccessType, ChargeType, CheckMode, YearRange}
import play.api.i18n.Messages
import uk.gov.hmrc.viewmodels.SummaryList.{Action, Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels._

import java.time.LocalDate


class CYAPublicPensionsRemedyHelper(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, chargeType: ChargeType)
                                   (implicit messages: Messages) extends CYAHelper {
  val chargeTypeDescription: String = Messages(s"chargeType.description.$chargeType")

  def isPsprForCharge(index: Int, isPSR: Option[Boolean]): Seq[Row] = {

    isPSR match {
      case Some(true) | Some(false) =>
        Seq(
          Row(
            key = Key(msg"${messages("isPublicServicePensionsRemedy.title", chargeTypeDescription)}", classes = Seq("govuk-!-width-one-half")),
            value = Value(getOptionalValue(isPSR), classes = Seq("govuk-!-width-one-third")),
            actions = List(
              Action(
                content = Html(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
                href = controllers.routes.IsPublicServicePensionsRemedyController
                  .onPageLoad(chargeType, CheckMode, srn, startDate, accessType, version, Some(index))
                  .url,
                visuallyHiddenText = Some(
                  Literal(
                    messages("site.edit") + " " + messages("isPublicServicePensionsRemedy.label")
                  ))
              )
            )
          )
        )
      case _ => Nil
    }
  }

  def psprChargeDetails(index: Int, pensionsRemedySummary: PensionsRemedySummary): Option[Seq[Row]] = {
    val isPublicServiceRemedy = getValueFromOptionBoolean(pensionsRemedySummary.isPublicServicePensionsRemedy)
    val isChargeInAdditionReported = getValueFromOptionBoolean(pensionsRemedySummary.isChargeInAdditionReported)
    (isPublicServiceRemedy, isChargeInAdditionReported) match {
      case (true, true) =>
        Some(
          getIsChargeInAdditionReportedRow(index, pensionsRemedySummary) ++
            getWasAnotherPensionSchemeRow(index, pensionsRemedySummary))
      case (true, false) =>
        Some(getIsChargeInAdditionReportedRow(index, pensionsRemedySummary))
      case (false, _) => None
    }
  }

  def getIsChargeInAdditionReportedRow(index: Int, pensionsRemedySummary: PensionsRemedySummary): Seq[Row] = {
    Seq(
      Row(
        key = Key(msg"${messages("isChargeInAdditionReported.title", chargeTypeDescription)}", classes = Seq("govuk-!-width-one-half")),
        value = Value(getOptionalValue(pensionsRemedySummary.isChargeInAdditionReported), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = Html(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
            href = controllers.mccloud.routes.IsChargeInAdditionReportedController
              .onPageLoad(chargeType, CheckMode, srn, startDate, accessType, version, index)
              .url,
            visuallyHiddenText = Some(
              Literal(
                messages("site.edit") + " " + messages("isChargeInAdditionReported.label", chargeTypeDescription)
              ))
          )
        )
      )
    )
  }

  def getWasAnotherPensionSchemeRow(index: Int, pensionsRemedySummary: PensionsRemedySummary): Seq[Row] = {
    Seq(
      Row(
        key = Key(msg"${messages("wasAnotherPensionScheme.title", chargeTypeDescription)}", classes = Seq("govuk-!-width-one-half")),
        value = Value(getOptionalValue(pensionsRemedySummary.wasAnotherPensionScheme), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = Html(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
            href = controllers.mccloud.routes.WasAnotherPensionSchemeController
              .onPageLoad(chargeType, CheckMode, srn, startDate, accessType, version, index)
              .url,
            visuallyHiddenText = Some(
              Literal(
                messages("site.edit") + " " + messages("wasAnotherPensionScheme.label", chargeTypeDescription)
              ))
          )
        )
      )
    )
  }

  def psprSchemesChargeDetails(index: Int, pensionsRemedySummary: PensionsRemedySummary,
                               wasAnotherPensionSchemeVal: Boolean): Seq[Row] = {
    {
      if (wasAnotherPensionSchemeVal) {
        for (pensionsRemedySchemeSummary <- pensionsRemedySummary.pensionsRemedySchemeSummary)
          yield pensionsRemedySchemeSummaryDetails(index, pensionsRemedySchemeSummary)
      } else {
        for (pensionsRemedySchemeSummary <- pensionsRemedySummary.pensionsRemedySchemeSummary)
          yield pensionsRemedySummaryDetails(index, pensionsRemedySchemeSummary, wasAnotherPensionSchemeVal = false)
      }
    }.flatten
  }

  def pensionsRemedySchemeSummaryDetails(index: Int, pensionsRemedySchemeSummary: PensionsRemedySchemeSummary): Seq[Row] = {
    val basicSchemeRows = Seq(
      Row(
        key = Key(
          msg"${messages(s"mccloud.scheme.cya.ref${pensionsRemedySchemeSummary.schemeIndex}")}",
          classes = Seq("govuk-!-width-full govuk-heading-m govuk-!-display-block govuk-!-margin-top-7")
        ),
        value = Value(Html(""), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = Html(s"<span  aria-hidden=true >${messages("site.remove")}</span>"),
            href = controllers.mccloud.routes.RemovePensionSchemeController
              .onPageLoad(chargeType, CheckMode, srn, startDate, accessType, version, index, pensionsRemedySchemeSummary.schemeIndex).url,
            visuallyHiddenText = Some(
              Literal(
                messages("site.remove") + " " + messages(s"mccloud.scheme.cya.ref${pensionsRemedySchemeSummary.schemeIndex}").toLowerCase
              ))
          )
        )
      ),
      Row(
        key = Key(
          msg"${messages("enterPstr.cya.label", messages(s"mccloud.scheme.ref${pensionsRemedySchemeSummary.schemeIndex}"), chargeTypeDescription)}",
          classes = Seq("govuk-!-width-one-half")
        ),
        value = Value(getOptionalLiteralValue(pensionsRemedySchemeSummary.pstrNumber), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = Html(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
            href = controllers.mccloud.routes.EnterPstrController
              .onPageLoad(chargeType, CheckMode, srn, startDate, accessType, version, index, pensionsRemedySchemeSummary.schemeIndex)
              .url,
            visuallyHiddenText = Some(
              Literal(
                messages("site.edit") + " " + messages("enterPstr.cya.visuallyHidden.text",
                  messages(s"mccloud.scheme.ref${pensionsRemedySchemeSummary.schemeIndex}"),
                  chargeTypeDescription)
              ))
          )
        )
      )
    )
    val taxPeriodAmtRows = pensionsRemedySummaryDetails(index, pensionsRemedySchemeSummary, wasAnotherPensionSchemeVal = true)
    basicSchemeRows ++ taxPeriodAmtRows
  }

  //scalastyle:off method.length
  //scalastyle:off cyclomatic.complexity
  def pensionsRemedySummaryDetails(index: Int,
                                   pensionsRemedySchemeSummary: PensionsRemedySchemeSummary,
                                   wasAnotherPensionSchemeVal: Boolean): Seq[Row] = {

    val schemeIndex = if (wasAnotherPensionSchemeVal) {
      Some(pensionsRemedySchemeSummary.schemeIndex)
    } else {
      None
    }

    Seq(
      Row(
        key = Key(
          msg"${
            messages("taxYearReportedAndPaid.cya.label",
              messages(s"mccloud.scheme.ref${pensionsRemedySchemeSummary.schemeIndex}"), chargeTypeDescription)
          }",
          classes = Seq("govuk-!-width-one-half")
        ),
        value = Value(getOptionalYearValue(pensionsRemedySchemeSummary.taxYear), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = Html(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
            href = controllers.mccloud.routes.TaxYearReportedAndPaidController
              .onPageLoad(chargeType, CheckMode, srn, startDate, accessType, version, index, schemeIndex)
              .url,
            visuallyHiddenText = Some(
              Literal(
                messages("site.edit") + " " + messages("taxYearReportedAndPaid.cya.visuallyHidden.text",
                  messages(s"mccloud.scheme.ref${pensionsRemedySchemeSummary.schemeIndex}"),
                  chargeTypeDescription)
              ))
          )
        )
      ),
      Row(
        key = Key(
          msg"${
            messages(
              "taxQuarterReportedAndPaid.cya.label",
              getOptionalYearForKey(pensionsRemedySchemeSummary.taxYear),
              messages(s"mccloud.scheme.ref${pensionsRemedySchemeSummary.schemeIndex}"),
              chargeTypeDescription
            )
          }",
          classes = Seq("govuk-!-width-one-half")
        ),
        value = Value(getOptionalLiteralQuarterValue(pensionsRemedySchemeSummary.taxQuarter), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = Html(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
            href = controllers.mccloud.routes.TaxYearReportedAndPaidController
              .onPageLoad(chargeType, CheckMode, srn, startDate, accessType, version, index, schemeIndex)
              .url,
            visuallyHiddenText = Some(
              Literal(messages("site.edit") + " " + messages(
                "taxQuarterReportedAndPaid.cya.visuallyHidden.text",
                getOptionalYearForKey(pensionsRemedySchemeSummary.taxYear),
                messages(s"mccloud.scheme.ref${pensionsRemedySchemeSummary.schemeIndex}"),
                chargeTypeDescription
              )))
          )
        )
      ),
      Row(
        key = Key(
          msg"${
            messages(
              "chargeAmountReported.cya.label",
              messages(s"mccloud.scheme.ref${pensionsRemedySchemeSummary.schemeIndex}"),
              chargeTypeDescription,
              getOptionalQuarterValue(pensionsRemedySchemeSummary.taxQuarter)
            )
          }",
          classes = Seq("govuk-!-width-one-half")
        ),
        value = Value(
          Literal(s"${FormatHelper.formatCurrencyAmountAsString(pensionsRemedySchemeSummary.chargeAmountReported.getOrElse(BigDecimal(0.00)))}"),
          classes = Seq("govuk-!-width-one-third")
        ),
        actions = List(
          Action(
            content = Html(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
            href = controllers.mccloud.routes.ChargeAmountReportedController
              .onPageLoad(chargeType, CheckMode, srn, startDate, accessType, version, index, schemeIndex)
              .url,
            visuallyHiddenText = Some(
              Literal(
                messages("site.edit") + " " + messages(
                  "chargeAmountReported.cya.visuallyHidden.text",
                  messages(s"mccloud.scheme.ref${pensionsRemedySchemeSummary.schemeIndex}"),
                  chargeTypeDescription,
                  getOptionalQuarterValue(pensionsRemedySchemeSummary.taxQuarter)
                )
              ))
          )
        )
      )
    )
  }

  private def getOptionalValue(optionalVal: Option[Boolean]): Content = {
    optionalVal match {
      case None => msg"mccloud.not.entered"
      case Some(booleanVal) => yesOrNo(booleanVal)
    }
  }

  private def getOptionalYearValue(optionalYearRange: Option[YearRange]): Content = {
    optionalYearRange match {
      case None => msg"mccloud.not.entered"
      case Some(yearRange) => YearRange.getLabel(yearRange)
    }
  }

  private def getOptionalLiteralValue(optionalString: Option[String]): Content = {
    optionalString match {
      case None => msg"mccloud.not.entered"
      case Some(stringVal) => Literal(stringVal.toUpperCase)
    }
  }

  private def getOptionalLiteralQuarterValue(optionalAFTQuarter: Option[AFTQuarter]): Content = {
    optionalAFTQuarter match {
      case None => msg"mccloud.not.entered"
      case Some(aftQuarter) => Literal(AFTQuarter.formatForDisplay(aftQuarter))
    }
  }

  private def getOptionalYearForKey(optionalYearRange: Option[YearRange]): String = {
    optionalYearRange match {
      case None => msg"mccloud.not.entered".resolve
      case Some(taxYear) =>
        val startYear = taxYear.toString
        msg"yearRangeRadio".withArgs(startYear, (startYear.toInt + 1).toString).resolve
    }
  }

  private def getOptionalQuarterValue(optionalAFTQuarter: Option[AFTQuarter]): String = {
    optionalAFTQuarter match {
      case None => msg"mccloud.not.entered".resolve
      case Some(aftQuarter) => AFTQuarter.formatForDisplay(aftQuarter)
    }
  }

  private def getValueFromOptionBoolean(optionalVal: Option[Boolean]): Boolean = {
    optionalVal match {
      case Some(booleanVal) => booleanVal
      case _ => false
    }
  }
}
