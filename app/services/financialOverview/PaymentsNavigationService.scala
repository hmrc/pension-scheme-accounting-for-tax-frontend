/*
 * Copyright 2022 HM Revenue & Customs
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

package services.financialOverview

import controllers.financialOverview.routes._
import models.financialStatement.PaymentOrChargeType._
import models.financialStatement.{PaymentOrChargeType, SchemeFS}
import play.api.mvc.Result
import play.api.mvc.Results.Redirect

import scala.concurrent.Future

class PaymentsNavigationService {

  def navFromSchemeDashboard(payments: Seq[SchemeFS], srn: String,  pstr: String): Future[Result] = {

    val paymentTypes: Seq[PaymentOrChargeType] = payments.map(p => getPaymentOrChargeType(p.chargeType)).distinct

    if (paymentTypes.size > 1) {
      Future.successful(Redirect(PaymentOrChargeTypeController.onPageLoad(srn, pstr)))
    } else if (paymentTypes.size == 1) {
      navFromPaymentsTypePage(payments, srn, pstr, paymentTypes.head)
    } else {
      Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

  def navFromPaymentsTypePage(payments: Seq[SchemeFS], srn: String,  pstr: String, paymentType: PaymentOrChargeType): Future[Result] = {

      val yearsSeq: Seq[Int] = payments
        .filter(p => getPaymentOrChargeType(p.chargeType) == paymentType)
        .map(_.periodEndDate.getYear).distinct.sorted.reverse

    (paymentType, yearsSeq.size) match {
      case (AccountingForTaxCharges, 1) => navFromAFTYearsPage(payments, yearsSeq.head, srn, pstr)
      case (_, 1) => Future.successful(Redirect(AllPaymentsAndChargesController.onPageLoad(srn, pstr, yearsSeq.head.toString, paymentType)))
      case (_, size) if size > 1 => Future.successful(Redirect(SelectYearController.onPageLoad(srn, pstr, paymentType)))
      case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
    }

  def navFromAFTYearsPage(payments: Seq[SchemeFS], year: Int, srn: String,  pstr: String): Future[Result] = {

    val quartersSeq = payments
      .filter(p => getPaymentOrChargeType(p.chargeType) == AccountingForTaxCharges)
      .filter(_.periodEndDate.getYear == year)
      .map(_.periodStartDate).distinct

    if (quartersSeq.size > 1) {
      Future.successful(Redirect(SelectQuarterController.onPageLoad(srn, pstr, year.toString)))
    } else if (quartersSeq.size == 1) {
      Future.successful(Redirect(AllPaymentsAndChargesController.onPageLoad(srn, pstr, quartersSeq.head.toString, AccountingForTaxCharges)))
    } else {
      Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

}
