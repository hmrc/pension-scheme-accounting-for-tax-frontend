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

package controllers.financialOverview.psa

import connectors.{FinancialStatementConnector, MinimalConnector}
import controllers.actions._
import models.ChargeDetailsFilter
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.AFTPartialService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.financialOverview.psa.RefundsView

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class RefundsController @Inject()(
                                   override val messagesApi: MessagesApi,
                                   identify: IdentifierAction,
                                   allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                   val controllerComponents: MessagesControllerComponents,
                                   service: AFTPartialService,
                                   financialStatementConnector: FinancialStatementConnector,
                                   minimalConnector: MinimalConnector,
                                   view: RefundsView
                                 )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {


  def onPageLoad(journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess()).async { implicit request =>

      minimalConnector.getPsaOrPspName.flatMap { name =>
        val requestRefundUrl = controllers.financialOverview.routes.RefundUnavailableController.onPageLoad.url
        financialStatementConnector.getPsaFSWithPaymentOnAccount(request.psaIdOrException.id).flatMap{
          psaFSWithPaymentOnAccount =>

            val latestCredits = psaFSWithPaymentOnAccount.seqPsaFSDetail
              .filter(_.dueDate.nonEmpty)
              .filter(_.amountDue < 0)
              .sortBy(_.dueDate)(Ordering[Option[LocalDate]].reverse).take(3)
            val creditBalance = service.getCreditBalanceAmount(latestCredits)
            val creditTable = service.getLatestCreditsDetails(latestCredits)
            Future.successful(Ok(view(
              journeyType = journeyType,
              name,
              creditBalance,
              requestRefundUrl,
              creditTable
            )))
        }
      }

    }
}
