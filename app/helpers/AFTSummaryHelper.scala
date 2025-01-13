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

import controllers._
import controllers.chargeB.{routes => _}
import models.AccessMode.PageAccessModeCompile
import models.ChargeType._
import models.LocalDateBinder._
import models.requests.DataRequest
import models.{AccessType, ChargeType, UserAnswers}
import play.api.i18n.Messages
import play.api.mvc.Call
import play.twirl.api.{Html => TwirlHtml}
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.{HtmlContent, Text}
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{ActionItem, Actions, Key, SummaryListRow, Value}

import java.time.LocalDate

class AFTSummaryHelper {

  case class SummaryDetails(chargeType: ChargeType, totalAmount: BigDecimal, href: Call)

  private def summaryDataUK(ua: UserAnswers,
    srn: String,
    startDate: LocalDate,
    accessType: AccessType,
    version: Int): Seq[SummaryDetails] = Seq(
    SummaryDetails(
      chargeType = ChargeTypeAnnualAllowance,
      totalAmount = ua.get(pages.chargeE.TotalChargeAmountPage).getOrElse(BigDecimal(0)),
      href = chargeE.routes.AddMembersController.onPageLoad(srn, startDate, accessType, version)
    ),
    SummaryDetails(
      chargeType = ChargeTypeAuthSurplus,
      totalAmount = ua.get(pages.chargeC.TotalChargeAmountPage).getOrElse(BigDecimal(0)),
      href = chargeC.routes.AddEmployersController.onPageLoad(srn, startDate, accessType, version)
    ),
    SummaryDetails(
      chargeType = ChargeTypeDeRegistration,
      totalAmount = ua.get(pages.chargeF.ChargeDetailsPage).map(_.totalAmount).getOrElse(BigDecimal(0)),
      href = chargeF.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version)
    ),
    SummaryDetails(
      chargeType = ChargeTypeLifetimeAllowance,
      totalAmount = ua.get(pages.chargeD.TotalChargeAmountPage).getOrElse(BigDecimal(0.00)),
      href = chargeD.routes.AddMembersController.onPageLoad(srn, startDate, accessType, version)
    ),
    SummaryDetails(
      chargeType = ChargeTypeShortService,
      totalAmount = ua.get(pages.chargeA.ChargeDetailsPage).map(_.totalAmount).getOrElse(BigDecimal(0)),
      href = chargeA.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version)
    ),
    SummaryDetails(
      chargeType = ChargeTypeLumpSumDeath,
      totalAmount = ua.get(pages.chargeB.ChargeBDetailsPage).map(_.totalAmount).getOrElse(BigDecimal(0)),
      href = chargeB.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version)
    )
  )

  private def summaryDataNonUK(ua: UserAnswers,
    srn: String,
    startDate: LocalDate,
    accessType: AccessType,
    version: Int): Seq[SummaryDetails] = Seq(
    SummaryDetails(
      chargeType = ChargeTypeOverseasTransfer,
      totalAmount = ua.get(pages.chargeG.TotalChargeAmountPage).getOrElse(BigDecimal(0)),
      href = chargeG.routes.AddMembersController.onPageLoad(srn, startDate, accessType, version)
    )
  )

  private def summaryRowsUK(ua: UserAnswers,
                            srn: String,
                            startDate: LocalDate,
                            accessType: AccessType,
                            version: Int)(implicit messages:Messages): Seq[SummaryListRow] =
    summaryDataUK(ua, srn, startDate, accessType, version).map { data =>
      SummaryListRow(
        key = Key(Text(messages(s"aft.summary.${data.chargeType.toString}.row")), classes = "govuk-!-width-three-quarters"),
        value = Value(Text(s"${FormatHelper.formatCurrencyAmountAsString(data.totalAmount)}"),
          classes = "govuk-!-width-one-quarter govuk-table__cell--numeric"),
        actions = if (data.totalAmount > BigDecimal(0)) {
          Some(
            Actions(
              items = Seq(ActionItem(
                content = HtmlContent(s"<span  aria-hidden=true >${messages("site.view")}</span>"),
                href = data.href.url,
                visuallyHiddenText = Some(
                  messages("site.view") + " " + messages(s"aft.summary.${data.chargeType.toString}.visuallyHidden.row")
                )
              ))
            )
          )
        } else {
          None
        }
      )
  }

  private def summaryRowsNonUK(ua: UserAnswers,
    srn: String,
    startDate: LocalDate,
    accessType: AccessType,
    version: Int)(implicit messages:Messages): Seq[SummaryListRow] =
    summaryDataNonUK(ua, srn, startDate, accessType, version).map { data =>
      SummaryListRow(
      key = Key(Text(messages("aft.summary.${data.chargeType.toString}.row")), classes = "govuk-!-width-three-quarters"),
      value = Value(Text(s"${FormatHelper.formatCurrencyAmountAsString(data.totalAmount)}"),
        classes = "govuk-!-width-one-quarter govuk-table__cell--numeric"),
      actions = if (data.totalAmount > BigDecimal(0)) {
        Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.view")}</span>"),
              href = data.href.url,
              visuallyHiddenText = Some(
                messages("site.view") + " " + messages(s"aft.summary.${data.chargeType.toString}.visuallyHidden.row")
              )
            ))
          )
        )
      } else {
        None
      }
    )
  }

  def summaryListData(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
                     (implicit messages: Messages): Seq[SummaryListRow] = {

    val totalRow: Seq[SummaryListRow] = Seq(SummaryListRow(
      key = Key(Text(messages("aft.summary.total")), classes = "govuk-table__header--numeric govuk-!-padding-right-0"),
      value = Value(Text(s"${FormatHelper.formatCurrencyAmountAsString(summaryDataUK(ua, srn, startDate, accessType, version).map(_.totalAmount).sum)}"),
        classes = "govuk-!-width-one-quarter govuk-table__cell--numeric"),
    ))

    summaryRowsUK(ua, srn, startDate, accessType, version) ++ totalRow ++ summaryRowsNonUK(ua, srn, startDate, accessType, version)
  }

  def viewAmendmentsLink(version: Int, srn: String, startDate: LocalDate, accessType: AccessType)
                        (implicit messages: Messages, request: DataRequest[_]): TwirlHtml = {

    val linkText = if (request.sessionData.sessionAccessData.accessMode == PageAccessModeCompile) {
      messages("allAmendments.view.changes.draft.link")
    } else {
      messages("allAmendments.view.changes.submission.link")
    }
    val viewAllAmendmentsUrl = controllers.amend.routes.ViewAllAmendmentsController.onPageLoad(srn, startDate, accessType, version).url
    TwirlHtml(
      s"${TwirlHtml(s"""<a id=view-amendments-link href=$viewAllAmendmentsUrl class="govuk-link"> $linkText</a>""".stripMargin).toString()}")
  }
}
