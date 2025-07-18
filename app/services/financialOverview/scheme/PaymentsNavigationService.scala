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

package services.financialOverview.scheme

import models.ChargeDetailsFilter.All
import models.financialStatement.PaymentOrChargeType._
import models.financialStatement.{PaymentOrChargeType, SchemeFSDetail}
import play.api.mvc.Result
import play.api.mvc.Results.Redirect

import scala.concurrent.Future

class PaymentsNavigationService {

  def navFromSchemeDashboard(payments: Seq[SchemeFSDetail], srn: String): Future[Result] = {

    val paymentTypes: Seq[PaymentOrChargeType] = payments.map(p => getPaymentOrChargeType(p.chargeType)).distinct

    if (paymentTypes.size > 1) {
      Future.successful(Redirect(controllers. financialOverview.scheme.routes.PaymentOrChargeTypeController.onPageLoad(srn, All)))
    } else if (paymentTypes.size == 1) {
      navFromPaymentsTypePage(payments, srn, paymentTypes.head)
    } else {
      Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

  def navFromPaymentsTypePage(payments: Seq[SchemeFSDetail], srn: String, paymentType: PaymentOrChargeType): Future[Result] = {

    val yearsSeq: Seq[Int] = payments
      .filter(p => getPaymentOrChargeType(p.chargeType) == paymentType)
      .filter(_.periodEndDate.nonEmpty)
      .map(_.periodEndDate.get.getYear).distinct.sorted.reverse

    (paymentType, yearsSeq.size) match {
      case (AccountingForTaxCharges, 1) => navFromAFTYearsPage(payments, yearsSeq.head, srn)
      case (_, 1) => Future.successful(Redirect(controllers. financialOverview.scheme.routes.
        AllPaymentsAndChargesController.onPageLoad(srn, yearsSeq.head.toString, paymentType)))
      case (_, size) if size > 1 => Future.successful(Redirect(controllers. financialOverview.scheme.routes.
        SelectYearController.onPageLoad(srn, paymentType)))
      case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

  def navToSelectClearedChargesYear(srn: String, paymentType: PaymentOrChargeType): Future[Result] = {
    Future.successful(Redirect(controllers.financialOverview.scheme.routes.ClearedChargesSelectYearController.onPageLoad(srn, paymentType)))
  }

  def navFromAFTYearsPage(payments: Seq[SchemeFSDetail], year: Int, srn: String): Future[Result] = {

    val quartersSeq = payments
      .filter(p => getPaymentOrChargeType(p.chargeType) == AccountingForTaxCharges)
      .filter(_.periodEndDate.exists(_.getYear == year))
      .filter(_.periodStartDate.nonEmpty)
      .map(_.periodStartDate.get).distinct

    if (quartersSeq.size > 1) {
      Future.successful(Redirect(controllers. financialOverview.scheme.routes.SelectQuarterController.onPageLoad(srn, year.toString)))
    } else if (quartersSeq.size == 1) {
      Future.successful(Redirect(controllers. financialOverview.scheme.routes.AllPaymentsAndChargesController.
        onPageLoad(srn, quartersSeq.head.toString, AccountingForTaxCharges)))
    } else {
      Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

}
