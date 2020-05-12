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

package helpers

import java.time.LocalDate

import models.CheckMode
import models.LocalDateBinder._
import models.chargeD.ChargeDDetails
import play.api.i18n.Messages
import uk.gov.hmrc.viewmodels.SummaryList.{Action, Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels._

class CYAChargeDHelper(srn: String, startDate: LocalDate)(implicit messages: Messages) extends CYAHelper {


  def chargeDMemberDetails(index: Int, answer: models.MemberDetails): Seq[Row] = {
    Seq(
      Row(
        key = Key(msg"cya.memberName.label", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.fullName.toString), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeD.routes.MemberDetailsController.onPageLoad(CheckMode, srn, startDate, index).url,
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
            href = controllers.chargeD.routes.MemberDetailsController.onPageLoad(CheckMode, srn, startDate, index).url,
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
        value = Value(Literal(answer.dateOfEvent.format(FormatHelper.dateFormatter)), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeD.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, index).url,
            visuallyHiddenText = Some(msg"chargeDDetails.dateOfEvent.visuallyHidden.label")
          )
        )
      ),
      Row(
        key = Key(msg"taxAt25Percent.label", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(
          answer.taxAt25Percent.getOrElse(BigDecimal(0.00)))}"), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeD.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, index).url,
            visuallyHiddenText = Some(msg"taxAt25Percent.visuallyHidden.label")
          )
        )
      ),
      Row(
        key = Key(msg"taxAt55Percent.label", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(
          answer.taxAt55Percent.getOrElse(BigDecimal(0.00)))}"), classes = Seq("govuk-!-width-one-third")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeD.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, index).url,
            visuallyHiddenText = Some(msg"taxAt55Percent.visuallyHidden.label")
          )
        )
      )
    )
  }

}
