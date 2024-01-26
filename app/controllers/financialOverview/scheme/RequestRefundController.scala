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

package controllers.financialOverview.scheme

import config.FrontendAppConfig
import connectors.{FinancialInfoCreditAccessConnector, FinancialStatementConnector, MinimalConnector}
import controllers.actions._
import models.AdministratorOrPractitioner.Administrator
import models.CreditAccessType
import models.CreditAccessType.{AccessedByLoggedInPsaOrPsp, AccessedByOtherPsa, AccessedByOtherPsp}
import models.requests.IdentifierRequest
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc._
import renderer.Renderer
import services.{PsaSchemePartialService, SchemeService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class RequestRefundController @Inject()(appConfig: FrontendAppConfig,
                                        identify: IdentifierAction,
                                        override val messagesApi: MessagesApi,
                                        val controllerComponents: MessagesControllerComponents,
                                        renderer: Renderer,
                                        financialStatementConnector: FinancialStatementConnector,
                                        psaSchemePartialService: PsaSchemePartialService,
                                        schemeService: SchemeService,
                                        minimalConnector: MinimalConnector,
                                        financialInfoCreditAccessConnector: FinancialInfoCreditAccessConnector
                                       )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private def creditAccess(srn: String)(implicit request: IdentifierRequest[AnyContent]): Future[Option[CreditAccessType]] = {
    val id = request.idOrException
    request.schemeAdministratorType match {
      case Administrator => financialInfoCreditAccessConnector.creditAccessForSchemePsa(id, srn)
      case _ => financialInfoCreditAccessConnector.creditAccessForSchemePsp(id, srn)
    }
  }

  private def requestRefundURL(srn: String)(implicit request: IdentifierRequest[AnyContent]): Future[String] = {
    for {
      psaOrPspName <- minimalConnector.getPsaOrPspName
      schemeDetails <- schemeService.retrieveSchemeDetails(request.idOrException, srn, "srn")
      creditSchemeFS <- financialStatementConnector.getSchemeFSPaymentOnAccount(schemeDetails.pstr)
    } yield {
      if (creditSchemeFS.inhibitRefundSignal) {
        controllers.financialOverview.routes.RefundUnavailableController.onPageLoad.url
      } else {
        val pstr = schemeDetails.pstr
        val creditBalance = psaSchemePartialService.getCreditBalanceAmount(creditSchemeFS.seqSchemeFSDetail)
        val creditBalanceBaseUrl = appConfig.creditBalanceRefundLink
        request.schemeAdministratorType match {
          case Administrator => s"$creditBalanceBaseUrl?requestType=1&psaName=$psaOrPspName&pstr=$pstr&availAmt=$creditBalance"
          case _ => s"$creditBalanceBaseUrl?requestType=2&pspName=$psaOrPspName&pstr=$pstr&availAmt=$creditBalance"
        }
      }
    }
  }

  def onPageLoad(srn: String): Action[AnyContent] = identify.async { implicit request =>
    requestRefundURL(srn).flatMap { url =>
      creditAccess(srn).flatMap {
        case None => Future.successful(Redirect(Call("GET", url)))
        case Some(cat) =>
          renderPage(cat, url)
      }
    }
  }

  private def renderPage(creditAccessType: CreditAccessType, continueUrl: String)(implicit request: IdentifierRequest[AnyContent]): Future[Result] = {
    val (heading, p1) = creditAccessType match {
      case AccessedByLoggedInPsaOrPsp =>
        Tuple2("requestRefund.youAlready.h1", "requestRefund.youAlready.p1")
      case AccessedByOtherPsa =>
        Tuple2("requestRefund.psaAlready.h1", "requestRefund.psaAlready.p1")
      case AccessedByOtherPsp =>
        Tuple2("requestRefund.pspAlready.h1", "requestRefund.pspAlready.p1")
    }

    val json = Json.obj(
      "heading" -> heading,
      "p1" -> p1,
      "continueUrl" -> continueUrl
    )

    renderer.render("financialOverview/requestRefund.njk", json).map(Ok(_))

  }
}
