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

import connectors.FinancialStatementConnector
import controllers.actions._
import models.{ChargeDetailsFilter, SchemeDetails}
import models.requests.IdentifierRequest
import play.api.i18n.{I18nSupport, Messages}
import play.api.mvc._
import services.{AFTPartialService, SchemeService}
import services.financialOverview.psa.PsaPenaltiesAndChargesService
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.HtmlContent
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.Table
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.financialOverview.psa.PsaReceivedPaymentsView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PsaReceivedPaymentsController @Inject()(identify: IdentifierAction,
                                              allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                              val controllerComponents: MessagesControllerComponents,
                                              financialStatementConnector: FinancialStatementConnector,
                                              psaPenaltiesAndChargesService: PsaPenaltiesAndChargesService,
                                              service: AFTPartialService,
                                              schemeService: SchemeService,
                                              view: PsaReceivedPaymentsView
                                                      )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  def onPageLoad(journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess()).async { implicit request: IdentifierRequest[AnyContent] =>
      val response = for {
        schemeDetails <- schemeService.retrieveSchemeDetails(request.idOrException, "pstr", "schemeId")
        psaFSWithPaymentOnAccount <- financialStatementConnector.getPsaFS(request.psaIdOrException.id)
      } yield {
        val paidPenalties = service.retrievePaidPenaltiesAndCharges(psaFSWithPaymentOnAccount.seqPsaFSDetail)
        val paymentDetailsTable = psaPenaltiesAndChargesService.paidPenaltiesDetails(paidPenalties)

        renderPsaReceivedPaymentsPage(schemeDetails, paymentDetailsTable, paidPenalties.nonEmpty, journeyType)
      }

      response.flatten
    }

  private def renderPsaReceivedPaymentsPage(schemeDetails: SchemeDetails,
                                            paymentDetailsTable: Table,
                                            hasPayments: Boolean,
                                            journeyType: ChargeDetailsFilter)(implicit request: IdentifierRequest[AnyContent]): Future[Result] = {
    if (hasPayments) {
      Future.successful(Ok(view(
        schemeName = schemeDetails.schemeName,
        paymentsDetails = paymentDetailsTable,
        insetText = setInsetText,
        isRefund = false
      )))
    } else {
      Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

  private def setInsetText(implicit messages: Messages): HtmlContent = {
    HtmlContent(
      s"<p class=govuk-body>${messages("receivedPayments.insetText")}</p>"
    )
  }

}


