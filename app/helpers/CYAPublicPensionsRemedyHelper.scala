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
import models.mccloud.{PensionsRemedySchemeSummary, PensionsRemedySummary}
import models.{AFTQuarter, AccessType, ChargeType, CheckMode, YearRange}
import play.api.i18n.Messages
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.{HtmlContent, Text}
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{ActionItem, Actions, Key, SummaryListRow, Value}

import java.time.LocalDate


class CYAPublicPensionsRemedyHelper(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, chargeType: ChargeType)
                                   (implicit messages: Messages) extends CYAHelper {
  val chargeTypeDescription: String = Messages(s"chargeType.description.$chargeType")

  def isPsprForCharge(index: Int, isPSR: Option[Boolean]): Seq[SummaryListRow] = {

    isPSR match {
      case Some(true) | Some(false) =>
        Seq(
          SummaryListRow(
            key = Key(Text(messages(s"${messages("isPublicServicePensionsRemedy.title", chargeTypeDescription)}")), classes = "govuk-!-width-one-half"),
            value = Value(getOptionalValue(isPSR), classes = "govuk-!-width-one-third"),
            actions = Some(
              Actions(
                items = Seq(ActionItem(
                  content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
                  href = controllers.routes.IsPublicServicePensionsRemedyController
                    .onPageLoad(chargeType, CheckMode, srn, startDate, accessType, version, Some(index))
                    .url,
                  visuallyHiddenText = Some(
                      messages("site.edit") + " " + messages("isPublicServicePensionsRemedy.label")
                    )
                ))
              )
            )
          )
        )
      case _ => Nil
    }
  }

  def psprChargeDetails(index: Int, pensionsRemedySummary: PensionsRemedySummary): Option[Seq[SummaryListRow]] = {
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

  def getIsChargeInAdditionReportedRow(index: Int, pensionsRemedySummary: PensionsRemedySummary): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(messages(s"${messages("isChargeInAdditionReported.title", chargeTypeDescription)}")), classes = "govuk-!-width-one-half"),
        value = Value(getOptionalValue(pensionsRemedySummary.isChargeInAdditionReported), classes = "govuk-!-width-one-third"),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.mccloud.routes.IsChargeInAdditionReportedController
                .onPageLoad(chargeType, CheckMode, srn, startDate, accessType, version, index)
                .url,
              visuallyHiddenText = Some(
                  messages("site.edit") + " " + messages("isChargeInAdditionReported.label", chargeTypeDescription)
                )
            ))
          )
        )
      )
    )
  }

  def getWasAnotherPensionSchemeRow(index: Int, pensionsRemedySummary: PensionsRemedySummary): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(messages(s"${messages("wasAnotherPensionScheme.title", chargeTypeDescription)}")), classes = "govuk-!-width-one-half"),
        value = Value(getOptionalValue(pensionsRemedySummary.wasAnotherPensionScheme), classes = "govuk-!-width-one-third"),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.mccloud.routes.WasAnotherPensionSchemeController
                .onPageLoad(chargeType, CheckMode, srn, startDate, accessType, version, index)
                .url,
              visuallyHiddenText = Some(
                  messages("site.edit") + " " + messages("wasAnotherPensionScheme.label", chargeTypeDescription)
                )
            ))
          )
        )
      )
    )
  }

  def psprSchemesChargeDetails(index: Int, pensionsRemedySummary: PensionsRemedySummary,
                               wasAnotherPensionSchemeVal: Boolean): Seq[SummaryListRow] = {
    {
      if (wasAnotherPensionSchemeVal) {
        for (pensionsRemedySchemeSummary <- pensionsRemedySummary.pensionsRemedySchemeSummary.filter(_.chargeAmountReported.nonEmpty))
          yield pensionsRemedySchemeSummaryDetails(index, pensionsRemedySchemeSummary)
      } else {
        for (pensionsRemedySchemeSummary <- pensionsRemedySummary.pensionsRemedySchemeSummary.filter(_.chargeAmountReported.nonEmpty))
          yield pensionsRemedySummaryDetails(index, pensionsRemedySchemeSummary, wasAnotherPensionSchemeVal = false)
      }
    }.flatten
  }

  def pensionsRemedySchemeSummaryDetails(index: Int, pensionsRemedySchemeSummary: PensionsRemedySchemeSummary): Seq[SummaryListRow] = {
    val basicSchemeRows = Seq(
      SummaryListRow(
        key = Key(
          Text(s"${messages(s"mccloud.scheme.cya.ref${pensionsRemedySchemeSummary.schemeIndex}")}"),
          classes = "govuk-!-width-full govuk-heading-m govuk-!-display-block govuk-!-margin-top-7"
        ),
        value = Value(HtmlContent(""), classes = "govuk-!-width-one-third"),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.remove")}</span>"),
              href = controllers.mccloud.routes.RemovePensionSchemeController
                .onPageLoad(chargeType, CheckMode, srn, startDate, accessType, version, index, pensionsRemedySchemeSummary.schemeIndex).url,
              visuallyHiddenText = Some(
                  messages("site.remove") + " " + messages(s"mccloud.scheme.cya.ref${pensionsRemedySchemeSummary.schemeIndex}").toLowerCase
                )
            ))
          )
        )
      ),
      SummaryListRow(
        key = Key(
          Text(s"${messages("enterPstr.cya.label", messages(s"mccloud.scheme.ref${pensionsRemedySchemeSummary.schemeIndex}"), chargeTypeDescription)}"),
          classes = "govuk-!-width-one-half"
        ),
        value = Value(getOptionalLiteralValue(pensionsRemedySchemeSummary.pstrNumber), classes = "govuk-!-width-one-third"),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.mccloud.routes.EnterPstrController
                .onPageLoad(chargeType, CheckMode, srn, startDate, accessType, version, index, pensionsRemedySchemeSummary.schemeIndex)
                .url,
              visuallyHiddenText = Some(
                  messages("site.edit") + " " + messages("enterPstr.cya.visuallyHidden.text",
                    messages(s"mccloud.scheme.ref${pensionsRemedySchemeSummary.schemeIndex}"),
                    chargeTypeDescription)
                )
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
                                   wasAnotherPensionSchemeVal: Boolean): Seq[SummaryListRow] = {

    val schemeIndex = if (wasAnotherPensionSchemeVal) {
      Some(pensionsRemedySchemeSummary.schemeIndex)
    } else {
      None
    }

    Seq(
      SummaryListRow(
        key = Key(
          Text(s"${
            messages("taxYearReportedAndPaid.cya.label",
              messages(s"mccloud.scheme.ref${pensionsRemedySchemeSummary.schemeIndex}"), chargeTypeDescription)
          }"),
          classes = "govuk-!-width-one-half"
        ),
        value = Value(getOptionalYearValue(pensionsRemedySchemeSummary.taxYear), classes = "govuk-!-width-one-third"),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.mccloud.routes.TaxYearReportedAndPaidController
                .onPageLoad(chargeType, CheckMode, srn, startDate, accessType, version, index, schemeIndex)
                .url,
              visuallyHiddenText = Some(
                  messages("site.edit") + " " + messages("taxYearReportedAndPaid.cya.visuallyHidden.text",
                    messages(s"mccloud.scheme.ref${pensionsRemedySchemeSummary.schemeIndex}"),
                    chargeTypeDescription)
                )
            ))
          )
        )
      ),
      SummaryListRow(
        key = Key(
          Text(s"${
            messages(
              "taxQuarterReportedAndPaid.cya.label",
              getOptionalYearForKey(pensionsRemedySchemeSummary.taxYear),
              messages(s"mccloud.scheme.ref${pensionsRemedySchemeSummary.schemeIndex}"),
              chargeTypeDescription
            )
          }"),
          classes = "govuk-!-width-one-half"
        ),
        value = Value(getOptionalLiteralQuarterValue(pensionsRemedySchemeSummary.taxQuarter), classes = "govuk-!-width-one-third"),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.mccloud.routes.TaxYearReportedAndPaidController
                .onPageLoad(chargeType, CheckMode, srn, startDate, accessType, version, index, schemeIndex)
                .url,
              visuallyHiddenText = Some(
                messages("site.edit") + " " + messages(
                  "taxQuarterReportedAndPaid.cya.visuallyHidden.text",
                  getOptionalYearForKey(pensionsRemedySchemeSummary.taxYear),
                  messages(s"mccloud.scheme.ref${pensionsRemedySchemeSummary.schemeIndex}"),
                  chargeTypeDescription
                ))
            ))
          )
        )
      ),
      SummaryListRow(
        key = Key(
          Text(s"${
            messages(
              "chargeAmountReported.cya.label",
              messages(s"mccloud.scheme.ref${pensionsRemedySchemeSummary.schemeIndex}"),
              chargeTypeDescription,
              getOptionalQuarterValue(pensionsRemedySchemeSummary.taxQuarter)
            )
          }"),
          classes = "govuk-!-width-one-half"
        ),
        value = Value(
          Text(s"${FormatHelper.formatCurrencyAmountAsString(pensionsRemedySchemeSummary.chargeAmountReported.getOrElse(BigDecimal(0.00)))}"),
          classes = "govuk-!-width-one-third"
        ),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.mccloud.routes.ChargeAmountReportedController
                .onPageLoad(chargeType, CheckMode, srn, startDate, accessType, version, index, schemeIndex)
                .url,
              visuallyHiddenText = Some(
                  messages("site.edit") + " " + messages(
                    "chargeAmountReported.cya.visuallyHidden.text",
                    messages(s"mccloud.scheme.ref${pensionsRemedySchemeSummary.schemeIndex}"),
                    chargeTypeDescription,
                    getOptionalQuarterValue(pensionsRemedySchemeSummary.taxQuarter)
                  )
                )
            ))
          )
        )
      )
    )
  }

  private def getOptionalValue(optionalVal: Option[Boolean]): Text = {
    optionalVal match {
      case None => Text(messages("mccloud.not.entered"))
      case Some(booleanVal) => yesOrNo(booleanVal)
    }
  }

  private def getOptionalYearValue(optionalYearRange: Option[YearRange]): Text = {
    optionalYearRange match {
      case None => Text(messages("mccloud.not.entered"))
      case Some(yearRange) => getLabel(yearRange)
    }
  }

  private def getOptionalLiteralValue(optionalString: Option[String]): Text = {
    optionalString match {
      case None => Text(messages("mccloud.not.entered"))
      case Some(stringVal) => Text(stringVal.toUpperCase)
    }
  }

  private def getOptionalLiteralQuarterValue(optionalAFTQuarter: Option[AFTQuarter]): Text = {
    optionalAFTQuarter match {
      case None => Text(messages("mccloud.not.entered"))
      case Some(aftQuarter) => Text(AFTQuarter.monthDayStringFormat(aftQuarter))
    }
  }

  private def getOptionalYearForKey(optionalYearRange: Option[YearRange]): String = {
    optionalYearRange match {
      case None => messages("mccloud.not.entered")
      case Some(taxYear) =>
        val startYear = taxYear.toString
        messages("yearRangeRadio", startYear, (startYear.toInt + 1).toString)
    }
  }

  private def getOptionalQuarterValue(optionalAFTQuarter: Option[AFTQuarter]): String = {
    optionalAFTQuarter match {
      case None => messages("mccloud.not.entered")
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
