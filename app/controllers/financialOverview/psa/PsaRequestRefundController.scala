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

import config.FrontendAppConfig
import connectors.{FinancialInfoCreditAccessConnector, FinancialStatementConnector, MinimalConnector}
import controllers.actions._
import models.requests.IdentifierRequest
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc._
import services.AFTPartialService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.financialOverview.RequestRefundView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PsaRequestRefundController @Inject()(appConfig: FrontendAppConfig,
                                           identify: IdentifierAction,
                                           override val messagesApi: MessagesApi,
                                           val controllerComponents: MessagesControllerComponents,
                                           requestRefundView: RequestRefundView,
                                           financialStatementConnector: FinancialStatementConnector,
                                           service: AFTPartialService,
                                           minimalConnector: MinimalConnector,
                                           financialInfoCreditAccessConnector: FinancialInfoCreditAccessConnector
                                       )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  private def requestRefundURL(implicit request: IdentifierRequest[AnyContent]): Future[String] = {
    for {
      psaName <- minimalConnector.getPsaOrPspName
      creditPsaFS <- financialStatementConnector.getPsaFSWithPaymentOnAccount(request.psaIdOrException.id)
    } yield {
      val creditPsaFSDetails = creditPsaFS.seqPsaFSDetail
      val creditBalance = service.getCreditBalanceAmount(creditPsaFSDetails)
      val creditBalanceBaseUrl = appConfig.creditBalanceRefundLink

      s"$creditBalanceBaseUrl?requestType=3&psaName=$psaName&availAmt=$creditBalance"

    }
  }

  def onPageLoad: Action[AnyContent] = identify.async { implicit request =>
    requestRefundURL.flatMap { url =>
      financialInfoCreditAccessConnector.creditAccessForPsa(request.idOrException).flatMap {
        case None => Future.successful(Redirect(Call("GET", url)))
        case Some(_) =>
          renderPage(url)
      }
    }
  }

  private def renderPage(continueUrl: String)(implicit request: IdentifierRequest[AnyContent], messages: Messages): Future[Result] = {

    Future.successful(Ok(requestRefundView(
      heading = "requestRefund.youAlready.h1",
      p1 = "requestRefund.youAlready.psa.p1",
      continueUrl = continueUrl
    )(request, messages)))

  }
}
