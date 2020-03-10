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

import controllers._
import controllers.chargeB.{routes => _}
import models.{ChargeType, UserAnswers}
import play.api.i18n.Messages
import play.api.mvc.Call
import uk.gov.hmrc.viewmodels.SummaryList.{Action, Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{SummaryList, _}
import models.ChargeType._
import utils.CheckYourAnswersHelper.formatCurrencyAmountAsString
import java.time.LocalDate
import models.LocalDateBinder._

class AFTSummaryHelper {

  case class SummaryDetails(chargeType: ChargeType, totalAmount: BigDecimal, href: Call)

  def summaryListData(ua: UserAnswers, srn: String, startDate: LocalDate)(implicit messages: Messages): Seq[Row] = {

    val summaryDataUK: Seq[SummaryDetails] = Seq(
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

    val summaryDataNonUK: Seq[SummaryDetails] = Seq(
      SummaryDetails(
        chargeType = ChargeTypeOverseasTransfer,
        totalAmount = ua.get(pages.chargeG.TotalChargeAmountPage).getOrElse(BigDecimal(0)),
        href = chargeG.routes.AddMembersController.onPageLoad(srn, startDate)
      )
    )

    val summaryRowsUK: Seq[SummaryList.Row] = summaryDataUK.map { data =>
      Row(
        key = Key(msg"aft.summary.${data.chargeType.toString}.row", classes = Seq("govuk-!-width-one-half")),
        value = Value(
          content = Literal(s"${formatCurrencyAmountAsString(data.totalAmount)}"),
          classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")
        ),
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


    val summaryRowsNonUK: Seq[SummaryList.Row] = summaryDataNonUK.map { data =>
      Row(
        key = Key(msg"aft.summary.${data.chargeType.toString}.row", classes = Seq("govuk-!-width-one-half")),
        value = Value(
          content = Literal(s"${formatCurrencyAmountAsString(data.totalAmount)}"),
          classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")
        ),
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

    val totalRow: Seq[SummaryList.Row] = Seq(Row(
      key = Key(msg"aft.summary.total", classes = Seq("govuk-table__header--numeric")),
      value = Value(
        content = Literal(s"${formatCurrencyAmountAsString(summaryDataUK.map(_.totalAmount).sum)}"),
        classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")
      ),
      actions = Nil
    ))

    summaryRowsUK ++ totalRow ++ summaryRowsNonUK
  }
}
