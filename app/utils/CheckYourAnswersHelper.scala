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

import java.time.format.DateTimeFormatter

import models.{CheckMode, UserAnswers}
import pages.chargeF.ChargeDetailsPage
import play.api.i18n.Messages
import uk.gov.hmrc.viewmodels.SummaryList._
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels._
import utils.CheckYourAnswersHelper._

class CheckYourAnswersHelper(userAnswers: UserAnswers, srn: String)(implicit messages: Messages) {

  def date: Option[Row] = userAnswers.get(ChargeDetailsPage) map {
    answer =>
      Row(
        key = Key(msg"chargeDetails.date.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.deRegistrationDate.format(dateFormatter))),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeF.routes.ChargeDetailsController.onPageLoad(CheckMode, srn).url,
            visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"chargeDetails.checkYourAnswersLabel"))
          )
        )
      )
  }

  def amount: Option[Row] = userAnswers.get(pages.chargeF.ChargeDetailsPage) map {
    answer =>
      Row(
        key = Key(msg"chargeDetails.amount.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.amountTaxDue.toString())),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeF.routes.ChargeDetailsController.onPageLoad(CheckMode, srn).url,
            visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"chargeDetails.checkYourAnswersLabel"))
          )
        )
      )
  }

  def chargeAMembers: Option[Row] = userAnswers.get(pages.chargeA.ChargeDetailsPage) map {
    answer =>
      Row(
        key = Key(msg"chargeA.chargeDetails.members.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.members.toString)),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeA.routes.ChargeDetailsController.onPageLoad(CheckMode, srn).url,
            visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"chargeA.chargeDetails.members.checkYourAnswersLabel"))
          )
        )
      )
  }

  def chargeAAmount20pc: Option[Row] = userAnswers.get(pages.chargeA.ChargeDetailsPage) map {
    answer =>
      Row(
        key = Key(msg"chargeA.chargeDetails.amount20pc.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.amountTaxDue20pc.toString())),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeA.routes.ChargeDetailsController.onPageLoad(CheckMode, srn).url,
            visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"chargeA.chargeDetails.amount20pc.checkYourAnswersLabel"))
          )
        )
      )
  }

  def chargeAAmount50pc: Option[Row] = userAnswers.get(pages.chargeA.ChargeDetailsPage) map {
    answer =>
      Row(
        key = Key(msg"chargeA.chargeDetails.amount50pc.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(answer.amountTaxDue50pc.toString())),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeA.routes.ChargeDetailsController.onPageLoad(CheckMode, srn).url,
            visuallyHiddenText = Some(msg"site.edit.hidden".withArgs(msg"chargeA.chargeDetails.amount50pc.checkYourAnswersLabel"))
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

  private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
}
