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
import controllers.chargeB.{routes => _}
import models.AmendedChargeStatus.{Added, Deleted, Unknown, Updated}
import models.ChargeType.{ChargeTypeDeRegistration, ChargeTypeLumpSumDeath, ChargeTypeShortService}
import models.requests.DataRequest
import models.viewModels.ViewAmendmentDetails
import models.{AmendedChargeStatus, ChargeType, UserAnswers}
import pages.QuestionPage
import pages.chargeA.{ChargeDetailsPage => ChargeADetailsPage}
import pages.chargeB.ChargeBDetailsPage
import pages.chargeF.{ChargeDetailsPage => ChargeFDetailsPage}
import play.api.i18n.Messages
import play.api.libs.json.{JsResultException, JsValue, Reads}
import play.api.mvc.AnyContent
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels._

class AmendmentHelper {

  def getTotalAmount(ua: UserAnswers): (BigDecimal, BigDecimal) = {
    val amountUK = Seq(
      ua.get(pages.chargeE.TotalChargeAmountPage).getOrElse(BigDecimal(0)),
      ua.get(pages.chargeC.TotalChargeAmountPage).getOrElse(BigDecimal(0)),
      ua.get(pages.chargeF.ChargeDetailsPage).map(_.amountTaxDue).getOrElse(BigDecimal(0)),
      ua.get(pages.chargeD.TotalChargeAmountPage).getOrElse(BigDecimal(0)),
      ua.get(pages.chargeA.ChargeDetailsPage).map(_.totalAmount).getOrElse(BigDecimal(0)),
      ua.get(pages.chargeB.ChargeBDetailsPage).map(_.amountTaxDue).getOrElse(BigDecimal(0))
    ).sum

    val amountNonUK = ua.get(pages.chargeG.TotalChargeAmountPage).getOrElse(BigDecimal(0))

    (amountUK, amountNonUK)
  }

  def amendmentSummaryRows(currentTotalAmount: BigDecimal, previousTotalAmount: BigDecimal, currentVersion: Int, previousVersion: Int)(
      implicit messages: Messages): Seq[Row] = {
    val differenceAmount = currentTotalAmount - previousTotalAmount
    if (previousTotalAmount == 0 && currentTotalAmount == 0) {
      Nil
    } else {
      Seq(
        Row(
          key = Key(msg"confirmSubmitAFTReturn.total.for".withArgs(previousVersion), classes = Seq("govuk-!-width-three-quarters")),
          value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(previousTotalAmount)}"),
                        classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")),
          actions = Nil
        ),
        Row(
          key = Key(msg"confirmSubmitAFTReturn.total.for.draft", classes = Seq("govuk-!-width-three-quarters")),
          value = Value(
            Literal(s"${FormatHelper.formatCurrencyAmountAsString(currentTotalAmount)}"),
            classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")
          ),
          actions = Nil
        ),
        Row(
          key = Key(msg"confirmSubmitAFTReturn.difference", classes = Seq("govuk-!-width-three-quarters")),
          value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(differenceAmount)}"),
                        classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")),
          actions = Nil
        )
      )
    }
  }

  def getAllAmendments(currentUa: UserAnswers, previousUa: UserAnswers)(implicit request: DataRequest[AnyContent],
                                                                        messages: Messages): Seq[ViewAmendmentDetails] = {

    val allAmendmentsChargeA = amendmentsForSchemeLevelCharges(currentUa,
                                                               previousUa,
                                                               ChargeTypeShortService,
                                                               chargeAmountPath = "totalAmount",
                                                               Some("numberOfMembers"),
                                                               ChargeADetailsPage)

    val allAmendmentsChargeB = amendmentsForSchemeLevelCharges(currentUa,
                                                               previousUa,
                                                               ChargeTypeLumpSumDeath,
                                                               chargeAmountPath = "amountTaxDue",
                                                               Some("numberOfDeceased"),
                                                               ChargeBDetailsPage)

    val allAmendmentsChargeF =
      amendmentsForSchemeLevelCharges(currentUa, previousUa, ChargeTypeDeRegistration, chargeAmountPath = "amountTaxDue", None, ChargeFDetailsPage)

    val allAmendmentsForSchemeLevelCharges = Seq(allAmendmentsChargeA, allAmendmentsChargeB, allAmendmentsChargeF).flatten

    val allAmendmentsForMemberLevelCharges =
      ChargeCHelper.getAllAuthSurplusAmendments(currentUa) ++
        ChargeDHelper.getAllLifetimeAllowanceAmendments(currentUa) ++
        ChargeEHelper.getAllAnnualAllowanceAmendments(currentUa) ++
        ChargeGHelper.getAllOverseasTransferAmendments(currentUa)

    allAmendmentsForSchemeLevelCharges ++ allAmendmentsForMemberLevelCharges
  }

  private def amendmentsForSchemeLevelCharges[A](
      currentUa: UserAnswers,
      previousUa: UserAnswers,
      chargeType: ChargeType,
      chargeAmountPath: String,
      noOfMembersPath: Option[String],
      chargeDetailsPage: QuestionPage[A])(implicit reads: Reads[A], messages: Messages): Option[ViewAmendmentDetails] = {

    val currentTotalAmount = currentUa.get(chargeDetailsPage.path \ chargeAmountPath).map(validate[BigDecimal](_)).getOrElse(BigDecimal(0))
    val previousTotalAmount = previousUa.get(chargeDetailsPage.path \ chargeAmountPath).map(validate[BigDecimal](_)).getOrElse(BigDecimal(0))

    //Set the correct Status for scheme level charges
    val amendedStatus = (previousUa.get(chargeDetailsPage), currentUa.get(chargeDetailsPage)) match {
      case Tuple2(None, Some(_))                                                    => Added
      case Tuple2(Some(_), Some(_)) if currentTotalAmount == BigDecimal(0)          => Deleted
      case Tuple2(Some(_), Some(_)) if !(currentTotalAmount == previousTotalAmount) => Updated
      case _                                                                        => Unknown
    }

    val numberOfMembers = noOfMembersPath
      .flatMap { nomPath =>
        currentUa.get(chargeDetailsPage.path \ nomPath).map(validate[Int](_)).map(nom => messages("allAmendments.numberOfMembers", nom))
      }
      .getOrElse(messages("allAmendments.noMembers"))

    //For deleted scheme level charge the current amount will be 0, so, show the amount from the previous version
    val amount = if (currentTotalAmount == 0) previousTotalAmount else currentTotalAmount

    if (AmendedChargeStatus.validStatus.contains(amendedStatus)) {
      Some(
        ViewAmendmentDetails(
          numberOfMembers,
          chargeType.toString,
          FormatHelper.formatCurrencyAmountAsString(amount),
          amendedStatus
        )
      )
    } else {
      None
    }
  }

  private def validate[A](jsValue: JsValue)(implicit rds: Reads[A]): A = {
    jsValue
      .validate[A]
      .fold(
        invalid = errors => throw JsResultException(errors),
        valid = response => response
      )
  }

}
