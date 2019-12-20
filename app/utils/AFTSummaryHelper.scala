/*
 * Copyright 2019 HM Revenue & Customs
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
import controllers.chargeB.{routes => _, _}
import models.{ChargeType, UserAnswers}
import play.api.i18n.Messages
import play.api.mvc.Call
import uk.gov.hmrc.viewmodels.SummaryList.{Action, Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{SummaryList, _}
import models.ChargeType._

class AFTSummaryHelper{

  case class SummaryDetails(chargeType: ChargeType, totalAmount: BigDecimal, href: Call)

  def summaryListData(ua: UserAnswers, srn: String)(implicit messages: Messages): Seq[Row] = {

    val summaryData: Seq[SummaryDetails] = Seq(
      SummaryDetails(
        chargeType = ChargeTypeAnnualAllowance,
        totalAmount = ua.get(pages.chargeE.TotalChargeAmountPage).getOrElse(BigDecimal(0)),
        href = chargeE.routes.AddMembersController.onPageLoad(srn)
      ),
      SummaryDetails(
        chargeType = ChargeTypeAuthSurplus,
        totalAmount = BigDecimal(0),
        href = routes.IndexController.onPageLoad()
      ),
      SummaryDetails(
        chargeType = ChargeTypeDeRegistration,
        totalAmount = ua.get(pages.chargeF.ChargeDetailsPage).map(_.amountTaxDue).getOrElse(BigDecimal(0)),
        href = chargeF.routes.CheckYourAnswersController.onPageLoad(srn)
      ),
      SummaryDetails(
        chargeType = ChargeTypeLifetimeAllowance,
        totalAmount = BigDecimal(0),
        href = routes.IndexController.onPageLoad()
      ),
      SummaryDetails(
        chargeType = ChargeTypeOverseasTransfer,
        totalAmount = BigDecimal(0),
        href = routes.IndexController.onPageLoad()
      ),
      SummaryDetails(
        chargeType = ChargeTypeShortService,
        totalAmount = ua.get(pages.chargeA.ChargeDetailsPage).map(_.totalAmount).getOrElse(BigDecimal(0)),
        href = chargeA.routes.CheckYourAnswersController.onPageLoad(srn)
      ),
      SummaryDetails(
        chargeType = ChargeTypeLumpSumDeath,
        totalAmount = ua.get(pages.chargeB.ChargeBDetailsPage).map(_.amountTaxDue).getOrElse(BigDecimal(0)),
        href = chargeB.routes.CheckYourAnswersController.onPageLoad(srn)
      )
    )

    val summaryRows: Seq[SummaryList.Row] = summaryData.map { data =>
      Row(
        key = Key(msg"aft.summary.${data.chargeType.toString}.row", classes = Seq("govuk-!-width-three-quarters")),
        value = Value(Literal(s"£${data.totalAmount}"), classes = Seq("govuk-!-width-one-quarter")),
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

    val totalRow: Row = Row(
      key = Key(msg"aft.summary.total", classes = Seq("govuk-table__header--numeric")),
      value = Value(Literal(s"£${summaryData.map(_.totalAmount).sum}"), classes = Seq("govuk-!-width-one-quarter")),
      actions = Nil
    )

    summaryRows :+ totalRow
  }
}
