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

import models.{ChargeType, UserAnswers}
import play.api.i18n.Messages
import play.api.mvc.Call
import uk.gov.hmrc.viewmodels.SummaryList.{Action, Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{SummaryList, _}

class AFTSummaryHelper{

  case class SummaryDetails(chargeType: ChargeType, totalAmount: BigDecimal, href: Call)

  def summaryListData(ua: UserAnswers, srn: String)(implicit messages: Messages): Seq[SummaryList.Row] = {

    val summaryData: Seq[SummaryDetails] = Seq(
      SummaryDetails(ChargeType.ChargeTypeAnnualAllowance,
        BigDecimal(0),
        controllers.chargeE.routes.WhatYouWillNeedController.onPageLoad(srn)),
      SummaryDetails(ChargeType.ChargeTypeAuthSurplus,
        BigDecimal(0),
        controllers.routes.IndexController.onPageLoad()),
      SummaryDetails(ChargeType.ChargeTypeDeRegistration,
        ua.get(pages.chargeF.ChargeDetailsPage).map(_.amountTaxDue).getOrElse(BigDecimal(0)),
        controllers.chargeF.routes.CheckYourAnswersController.onPageLoad(srn)),
      SummaryDetails(ChargeType.ChargeTypeLifetimeAllowance,
        BigDecimal(0),
        controllers.routes.IndexController.onPageLoad()),
      SummaryDetails(ChargeType.ChargeTypeOverseasTransfer,
        BigDecimal(0),
        controllers.routes.IndexController.onPageLoad()),
      SummaryDetails(ChargeType.ChargeTypeShortService,
        ua.get(pages.chargeA.ChargeDetailsPage).map(_.totalAmount).getOrElse(BigDecimal(0)),
        controllers.chargeA.routes.CheckYourAnswersController.onPageLoad(srn)),
      SummaryDetails(ChargeType.ChargeTypeLumpSumDeath,
        ua.get(pages.chargeB.ChargeBDetailsPage).map(_.amountTaxDue).getOrElse(BigDecimal(0)),
        controllers.chargeB.routes.CheckYourAnswersController.onPageLoad(srn))
    )

    summaryData.map { data =>
      Row(
        key = Key(msg"aft.summary.${data.chargeType.toString}.row", classes = Seq("govuk-!-width-one-half")),
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
  }
}
