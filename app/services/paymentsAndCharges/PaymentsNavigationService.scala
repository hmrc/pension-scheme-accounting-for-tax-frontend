/*
 * Copyright 2021 HM Revenue & Customs
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

package services.paymentsAndCharges


import controllers.Assets.Redirect
import controllers.financialStatement.paymentsAndCharges.routes._
import models.ChargeDetailsFilter
import models.ChargeDetailsFilter._
import models.financialStatement.PaymentOrChargeType._
import models.financialStatement.{PaymentOrChargeType, SchemeFS}
import play.api.mvc.Result

import scala.concurrent.Future

class PaymentsNavigationService {

  def navFromSchemeDashboard(payments: Seq[SchemeFS], srn: String, journeyType: ChargeDetailsFilter): Future[Result] = {

    val paymentTypes: Seq[PaymentOrChargeType] = payments.map(p => getPaymentOrChargeType(p.chargeType)).distinct

    if (paymentTypes.size > 1) {
      Future.successful(Redirect(PaymentOrChargeTypeController.onPageLoad(srn, journeyType)))
    } else if (paymentTypes.size == 1) {
      navFromPaymentsTypePage(payments, srn, paymentTypes.head, journeyType)
    } else {
      Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
    }
  }

  def navFromPaymentsTypePage(payments: Seq[SchemeFS], srn: String, paymentType: PaymentOrChargeType, journeyType: ChargeDetailsFilter): Future[Result] = {

      val yearsSeq: Seq[Int] = payments
        .filter(p => getPaymentOrChargeType(p.chargeType) == paymentType)
        .map(_.periodEndDate.getYear).distinct.sorted.reverse

    (paymentType, yearsSeq.size) match {
      case (AccountingForTaxCharges, 1) => navFromAFTYearsPage(payments, yearsSeq.head, srn, journeyType)
      case (_, 1) => Future.successful(Redirect(PaymentsAndChargesController.onPageLoad(srn, yearsSeq.head.toString, paymentType, journeyType)))
      case (_, size) if size > 1 => Future.successful(Redirect(SelectYearController.onPageLoad(srn, paymentType, journeyType)))
      case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
    }
    }

  def navFromAFTYearsPage(payments: Seq[SchemeFS], year: Int, srn: String, journeyType: ChargeDetailsFilter): Future[Result] = {

    val quartersSeq = payments
      .filter(p => getPaymentOrChargeType(p.chargeType) == AccountingForTaxCharges)
      .filter(_.periodEndDate.getYear == year)
      .map(_.periodStartDate).distinct

    if (quartersSeq.size > 1) {
      Future.successful(Redirect(SelectQuarterController.onPageLoad(srn, year.toString, journeyType)))
    } else if (quartersSeq.size == 1) {
      Future.successful(Redirect(PaymentsAndChargesController.onPageLoad(srn, quartersSeq.head.toString, AccountingForTaxCharges, journeyType)))
    } else {
      Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
    }
  }

}
