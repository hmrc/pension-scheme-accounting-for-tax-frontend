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

package services.paymentsAndCharges


import controllers.financialStatement.paymentsAndCharges.routes._
import models.ChargeDetailsFilter
import models.financialStatement.PaymentOrChargeType._
import models.financialStatement.{PaymentOrChargeType, SchemeFSDetail}
import play.api.mvc.Result
import play.api.mvc.Results.Redirect

import scala.concurrent.Future

class PaymentsNavigationService {

  def navFromSchemeDashboard(payments: Seq[SchemeFSDetail], srn: String, journeyType: ChargeDetailsFilter): Future[Result] = {

    val paymentTypes: Seq[PaymentOrChargeType] = payments.map(p => getPaymentOrChargeType(p.chargeType)).distinct

    if (paymentTypes.size > 1) {
      Future.successful(Redirect(PaymentOrChargeTypeController.onPageLoad(srn, journeyType)))
    } else if (paymentTypes.size == 1) {
      navFromPaymentsTypePage(payments, srn, paymentTypes.head, journeyType)
    } else {
      Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

  def navFromPaymentsTypePage(payments: Seq[SchemeFSDetail], srn: String,
                              paymentType: PaymentOrChargeType, journeyType: ChargeDetailsFilter): Future[Result] = {

    val yearsSeq: Seq[Int] = payments
      .filter(p => getPaymentOrChargeType(p.chargeType) == paymentType)
      .filter(_.periodEndDate.nonEmpty)
      .map(_.periodEndDate.get.getYear).distinct.sorted.reverse

    (paymentType, yearsSeq.size) match {
      case (AccountingForTaxCharges, 1) => navFromAFTYearsPage(payments, yearsSeq.head, srn, journeyType)
      case (EventReportingCharges, 1) => navFromERYearsPage(payments, yearsSeq.head, srn, journeyType)
      case (_, 1) => Future.successful(Redirect(PaymentsAndChargesController.onPageLoad(srn, yearsSeq.head.toString, paymentType, journeyType)))
      case (_, size) if size > 1 => Future.successful(Redirect(SelectYearController.onPageLoad(srn, paymentType, journeyType)))
      case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

  def navFromERYearsPage(payments: Seq[SchemeFSDetail], year: Int, srn: String, journeyType: ChargeDetailsFilter): Future[Result] = {
    val uniqueYears = payments
      .filter(p => getPaymentOrChargeType(p.chargeType) == EventReportingCharges)
      .map(_.periodStartDate).distinct

    uniqueYears.length match {
      case 0 => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
      case _ => Future.successful(Redirect(PaymentsAndChargesController.onPageLoad(srn, year.toString, EventReportingCharges, journeyType)))
    }
  }

  def navFromAFTYearsPage(payments: Seq[SchemeFSDetail], year: Int, srn: String, journeyType: ChargeDetailsFilter): Future[Result] = {

    val quartersSeq = payments
      .filter(p => getPaymentOrChargeType(p.chargeType) == AccountingForTaxCharges)
      .filter(_.periodEndDate.nonEmpty)
      .filter(_.periodEndDate.exists(_.getYear == year))
      .map(_.periodStartDate.get).distinct

    if (quartersSeq.size > 1) {
      Future.successful(Redirect(SelectQuarterController.onPageLoad(srn, year.toString, journeyType)))
    } else if (quartersSeq.size == 1) {
      Future.successful(Redirect(PaymentsAndChargesController.onPageLoad(srn, quartersSeq.head.toString, AccountingForTaxCharges, journeyType)))
    } else {
      Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

}
