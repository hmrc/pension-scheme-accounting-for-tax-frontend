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

package controllers.financialStatement.paymentsAndCharges

import controllers.actions._
import models.LocalDateBinder._
import models.Quarters
import models.financialStatement.{PsaFS, SchemeFS}
import models.requests.IdentifierRequest
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents, Result}
import services.PenaltiesService
import services.paymentsAndCharges.PaymentsAndChargesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PaymentsLogicController @Inject()(override val messagesApi: MessagesApi,
                                        service: PaymentsAndChargesService,
                                        identify: IdentifierAction,
                                        val controllerComponents: MessagesControllerComponents
                                      )(implicit ec: ExecutionContext)
                                          extends FrontendBaseController
                                          with I18nSupport
                                          with NunjucksSupport {

  def onPageLoad(srn: String): Action[AnyContent] = identify.async { implicit request =>

    service.getPaymentsFromCache(request.idOrException, srn).flatMap { paymentsCache =>
      val selectYearUrl: Call = routes.SelectYearController.onPageLoad(srn)
      val selectQuarterUrl: Int => Call = year => routes.SelectQuarterController.onPageLoad(srn, year.toString)
      val chargesOverviewUrl: LocalDate => Call = startDate => routes.PaymentsAndChargesController.onPageLoad(srn, startDate)

      redirectionLogic(paymentsCache.schemeFS, selectYearUrl, selectQuarterUrl, chargesOverviewUrl)

    }
  }

  def onPageLoadOverdue(srn: String): Action[AnyContent] = identify.async { implicit request =>

    service.getPaymentsFromCache(request.idOrException, srn).flatMap { paymentsCache =>

      val overduePaymentsAndCharges: Seq[SchemeFS] = service.getOverdueCharges(paymentsCache.schemeFS)
      val selectYearUrl: Call = routes.SelectYearController.onPageLoadOverdue(srn)
      val selectQuarterUrl: Int => Call = year => routes.SelectQuarterController.onPageLoadOverdue(srn, year.toString)
      val chargesOverviewUrl: LocalDate => Call = startDate => routes.PaymentsAndChargesOverdueController.onPageLoad(srn, startDate)

      redirectionLogic(overduePaymentsAndCharges, selectYearUrl, selectQuarterUrl, chargesOverviewUrl)

    }
  }

  private def redirectionLogic(payments: Seq[SchemeFS],
                    selectYearUrl: Call,
                    selectQuarterUrl: Int => Call,
                    chargesOverviewUrl: LocalDate => Call): Future[Result] = {

    val yearsSeq: Seq[Int] = payments.map(_.periodStartDate.getYear).distinct.sorted.reverse

    if (yearsSeq.nonEmpty && yearsSeq.size > 1) {
      Future.successful(Redirect(selectYearUrl))
    } else if (yearsSeq.size == 1) {
      skipYearsPage(payments, yearsSeq.head, selectQuarterUrl, chargesOverviewUrl)
    } else {
      Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
    }
  }

  private def skipYearsPage(payments: Seq[SchemeFS],
                            year: Int,
                            selectQuarterUrl: Int => Call,
                            chargesOverviewUrl: LocalDate => Call): Future[Result] = {
    val quartersSeq = payments
      .filter(_.periodStartDate.getYear == year)
      .map { paymentOrCharge => Quarters.getQuarter(paymentOrCharge.periodStartDate) }.distinct

    if (quartersSeq.size > 1) {
      Future.successful(Redirect(selectQuarterUrl(year)))
    } else if (quartersSeq.size == 1) {
      Future.successful(Redirect(chargesOverviewUrl(quartersSeq.head.startDate)))
    } else {
      Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
    }
  }
}
