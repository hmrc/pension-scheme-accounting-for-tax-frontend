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
import com.google.inject.Inject
import controllers.chargeB.{routes => _}
import models.AmendedChargeStatus.{Added, Deleted, Unknown, Updated}
import models.ChargeType.{ChargeTypeDeRegistration, ChargeTypeLumpSumDeath, ChargeTypeShortService}
import models.viewModels.ViewAmendmentDetails
import models.{AmendedChargeStatus, ChargeType, UserAnswers}
import pages.QuestionPage
import pages.chargeA.{ChargeDetailsPage => ChargeADetailsPage}
import pages.chargeB.ChargeBDetailsPage
import pages.chargeF.{ChargeDetailsPage => ChargeFDetailsPage}
import play.api.i18n.Messages
import play.api.libs.json.{JsResultException, JsValue, Reads}
import services.{ChargeCService, ChargeDService, ChargeEService, ChargeGService}
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{Key, SummaryListRow, Value}

class AmendmentHelper @Inject()(
                                 chargeCService: ChargeCService,
                                 chargeDService: ChargeDService,
                                 chargeEService: ChargeEService,
                                 chargeGService: ChargeGService
                               ) {

  def getTotalAmount(ua: UserAnswers): (BigDecimal, BigDecimal) = {
    val amountUK = Seq(
      ua.get(pages.chargeE.TotalChargeAmountPage).getOrElse(BigDecimal(0)),
      ua.get(pages.chargeC.TotalChargeAmountPage).getOrElse(BigDecimal(0)),
      ua.get(pages.chargeF.ChargeDetailsPage).map(_.totalAmount).getOrElse(BigDecimal(0)),
      ua.get(pages.chargeD.TotalChargeAmountPage).getOrElse(BigDecimal(0)),
      ua.get(pages.chargeA.ChargeDetailsPage).map(_.totalAmount).getOrElse(BigDecimal(0)),
      ua.get(pages.chargeB.ChargeBDetailsPage).map(_.totalAmount).getOrElse(BigDecimal(0))
    ).sum

    val amountNonUK = ua.get(pages.chargeG.TotalChargeAmountPage).getOrElse(BigDecimal(0))

    (amountUK, amountNonUK)
  }

  def amendmentSummaryRows(currentTotalAmount: BigDecimal, previousTotalAmount: BigDecimal, previousVersion: Int)(
    implicit messages: Messages): Seq[SummaryListRow] = {
    val differenceAmount = currentTotalAmount - previousTotalAmount
    if (previousTotalAmount == 0 && currentTotalAmount == 0) {
      Nil
    } else {
      Seq(
        SummaryListRow(
          key = Key(Text(messages("confirmSubmitAFTReturn.total.for", previousVersion)), classes = "govuk-!-width-three-quarters"),
          value = Value(Text(s"${FormatHelper.formatCurrencyAmountAsString(previousTotalAmount)}"),
                        classes = "govuk-!-width-one-quarter govuk-table__cell--numeric")
        ),
        SummaryListRow(
          key = Key(Text(messages("confirmSubmitAFTReturn.total.for.draft")), classes = "govuk-!-width-three-quarters"),
          value = Value(
            Text(s"${FormatHelper.formatCurrencyAmountAsString(currentTotalAmount)}"),
            classes = "govuk-!-width-one-quarter govuk-table__cell--numeric"
          )
        ),
        SummaryListRow(
          key = Key(Text(messages("confirmSubmitAFTReturn.difference")), classes = "govuk-!-width-three-quarters"),
          value = Value(Text(s"${FormatHelper.formatCurrencyAmountAsString(differenceAmount)}"),
                        classes = "govuk-!-width-one-quarter govuk-table__cell--numeric")
        )
      )
    }
  }

  def getAllAmendments(currentUa: UserAnswers, previousUa: UserAnswers, version: Int)(implicit messages: Messages): Seq[ViewAmendmentDetails] = {

    val allAmendmentsChargeA =
      amendmentsForSchemeLevelCharges(currentUa, previousUa, ChargeTypeShortService, Some("numberOfMembers"), ChargeADetailsPage)

    val allAmendmentsChargeB =
      amendmentsForSchemeLevelCharges(currentUa, previousUa, ChargeTypeLumpSumDeath, Some("numberOfDeceased"), ChargeBDetailsPage)

    val allAmendmentsChargeF =
      amendmentsForSchemeLevelCharges(currentUa, previousUa, ChargeTypeDeRegistration, None, ChargeFDetailsPage)

    val allAmendmentsForSchemeLevelCharges = Seq(allAmendmentsChargeA, allAmendmentsChargeB, allAmendmentsChargeF).flatten

    val allAmendmentsForMemberLevelCharges =
      chargeCService.getAllAuthSurplusAmendments(currentUa, version) ++
        chargeDService.getAllLifetimeAllowanceAmendments(currentUa, version) ++
        chargeEService.getAllAnnualAllowanceAmendments(currentUa, version) ++
        chargeGService.getAllOverseasTransferAmendments(currentUa, version)

    allAmendmentsForSchemeLevelCharges ++ allAmendmentsForMemberLevelCharges
  }

  private def amendmentsForSchemeLevelCharges[A](
      currentUa: UserAnswers,
      previousUa: UserAnswers,
      chargeType: ChargeType,
      noOfMembersPath: Option[String],
      chargeDetailsPage: QuestionPage[A])(implicit reads: Reads[A], messages: Messages): Option[ViewAmendmentDetails] = {

    val currentTotalAmount = currentUa.get(chargeDetailsPage.path \ "totalAmount").map(validate[BigDecimal](_)).getOrElse(BigDecimal(0))
    val previousTotalAmount = previousUa.get(chargeDetailsPage.path \ "totalAmount").map(validate[BigDecimal](_)).getOrElse(BigDecimal(0))

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
