package helpers

import java.time.LocalDate

import helpers.CYAHelper._
import models.CheckMode
import models.LocalDateBinder._
import models.chargeD.ChargeDDetails
import play.api.i18n.Messages
import uk.gov.hmrc.viewmodels.SummaryList.{Action, Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels._

class CYAChargeDHelper(srn: String, startDate: LocalDate)(implicit messages: Messages) {


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
        value = Value(Literal(answer.dateOfEvent.format(dateFormatter)), classes = Seq("govuk-!-width-one-third")),
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
        value = Value(Literal(s"${formatCurrencyAmountAsString(answer.taxAt25Percent.getOrElse(BigDecimal(0.00)))}"), classes = Seq("govuk-!-width-one-third")),
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
        value = Value(Literal(s"${formatCurrencyAmountAsString(answer.taxAt55Percent.getOrElse(BigDecimal(0.00)))}"), classes = Seq("govuk-!-width-one-third")),
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
