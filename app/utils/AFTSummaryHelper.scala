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

import java.time.LocalDate

import controllers._
import controllers.chargeB.{routes => _}
import helpers.{CYAHelper, FormatHelper}
import models.ChargeType._
import models.LocalDateBinder._
import models.Member
import models.{ChargeType, UserAnswers}
import play.api.i18n.Messages
import play.api.libs.json.Format
import play.api.libs.json.Json
import play.api.mvc.Call
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Value, Row, Action}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{SummaryList, _}

class AFTSummaryHelper {


  case class MemberSearch(index: Int, name: String, nino: String, chargeType: String, amount: BigDecimal, viewLink: String, removeLink: String, isDeleted: Boolean = false) {
    def id = s"member-$index"

    def removeLinkId = s"$id-remove"

    def viewLinkId = s"$id-view"

  }

  object MemberSearch {
    implicit lazy val formats: Format[Member] =
      Json.format[Member]
  }

  private def memberSearchResult(ua: UserAnswers, srn: String, startDate:LocalDate): Seq[MemberSearch] = {
    Nil
  }

  case class SummaryDetails(chargeType: ChargeType, totalAmount: BigDecimal, href: Call)

  private def summaryDataUK(ua: UserAnswers, srn: String, startDate: LocalDate): Seq[SummaryDetails] = Seq(
    SummaryDetails(
      chargeType = ChargeTypeAnnualAllowance,
      totalAmount = ua.get(pages.chargeE.TotalChargeAmountPage).getOrElse(BigDecimal(0)),
      href = chargeE.routes.AddMembersController.onPageLoad(srn, startDate)
    ),
    SummaryDetails(
      chargeType = ChargeTypeAuthSurplus,
      totalAmount = ua.get(pages.chargeC.TotalChargeAmountPage).getOrElse(BigDecimal(0)),
      href = chargeC.routes.AddEmployersController.onPageLoad(srn, startDate)
    ),
    SummaryDetails(
      chargeType = ChargeTypeDeRegistration,
      totalAmount = ua.get(pages.chargeF.ChargeDetailsPage).map(_.amountTaxDue).getOrElse(BigDecimal(0)),
      href = chargeF.routes.CheckYourAnswersController.onPageLoad(srn, startDate)
    ),
    SummaryDetails(
      chargeType = ChargeTypeLifetimeAllowance,
      totalAmount = ua.get(pages.chargeD.TotalChargeAmountPage).getOrElse(BigDecimal(0.00)),
      href = chargeD.routes.AddMembersController.onPageLoad(srn, startDate)
    ),
    SummaryDetails(
      chargeType = ChargeTypeShortService,
      totalAmount = ua.get(pages.chargeA.ChargeDetailsPage).map(_.totalAmount).getOrElse(BigDecimal(0)),
      href = chargeA.routes.CheckYourAnswersController.onPageLoad(srn, startDate)
    ),
    SummaryDetails(
      chargeType = ChargeTypeLumpSumDeath,
      totalAmount = ua.get(pages.chargeB.ChargeBDetailsPage).map(_.amountTaxDue).getOrElse(BigDecimal(0)),
      href = chargeB.routes.CheckYourAnswersController.onPageLoad(srn, startDate)
    )
  )

  private def summaryDataNonUK(ua: UserAnswers, srn: String, startDate: LocalDate): Seq[SummaryDetails] = Seq(
    SummaryDetails(
      chargeType = ChargeTypeOverseasTransfer,
      totalAmount = ua.get(pages.chargeG.TotalChargeAmountPage).getOrElse(BigDecimal(0)),
      href = chargeG.routes.AddMembersController.onPageLoad(srn, startDate)
    )
  )

  def memberSearchRowsUK(ua: UserAnswers, srn: String, startDate: LocalDate): Seq[SummaryList.Row] = {
    //memberSearchResult(ua, srn, startDate).map { data =>
    //  Row(
    //    key = Key(msg"aft.summary.${data.chargeType.toString}.row", classes = Seq("govuk-!-width-three-quarters")),
    //    value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(data.totalAmount)}"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")),
    //    actions = if (data.totalAmount > BigDecimal(0)) {
    //      List(
    //        Action(
    //          content = msg"site.view",
    //          href = data.href.url,
    //          visuallyHiddenText = Some(msg"aft.summary.${data.chargeType.toString}.visuallyHidden.row")
    //        )
    //      )
    //    } else {
    //      Nil
    //    }
    //  )
    //}
    Nil
  }

  private def summaryRowsUK(ua: UserAnswers, srn: String, startDate: LocalDate): Seq[SummaryList.Row] =
    summaryDataUK(ua, srn, startDate).map { data =>
    Row(
      key = Key(msg"aft.summary.${data.chargeType.toString}.row", classes = Seq("govuk-!-width-three-quarters")),
      value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(data.totalAmount)}"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")),
      actions = if (data.totalAmount > BigDecimal(0)) {
        List(
          Action(
            content = msg"site.view",
            href = data.href.url,
            visuallyHiddenText = Some(msg"aft.summary.${data.chargeType.toString}.visuallyHidden.row")
          )
        )
      } else {
        Nil
      }
    )
  }


  private def summaryRowsNonUK(ua: UserAnswers, srn: String, startDate: LocalDate): Seq[SummaryList.Row] =
    summaryDataNonUK(ua, srn, startDate).map { data =>
    Row(
      key = Key(msg"aft.summary.${data.chargeType.toString}.row", classes = Seq("govuk-!-width-three-quarters")),
      value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(data.totalAmount)}"), classes = Seq("govuk-!-width-one-quarter","govuk-table__cell--numeric")),
      actions = if (data.totalAmount > BigDecimal(0)) {
        List(
          Action(
            content = msg"site.view",
            href = data.href.url,
            visuallyHiddenText = Some(msg"aft.summary.${data.chargeType.toString}.visuallyHidden.row")
          )
        )
      } else {
        Nil
      }
    )
  }



  def summaryListData(ua: UserAnswers, srn: String, startDate: LocalDate)(implicit messages: Messages): Seq[Row] = {

    val totalRow: Seq[SummaryList.Row] = Seq(Row(
      key = Key(msg"aft.summary.total", classes = Seq("govuk-table__header--numeric","govuk-!-padding-right-0")),
      value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(summaryDataUK(ua, srn, startDate).map(_.totalAmount).sum)}"),
        classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")),
      actions = Nil
    ))

    summaryRowsUK(ua, srn, startDate) ++ totalRow ++ summaryRowsNonUK(ua, srn, startDate)
  }
}
