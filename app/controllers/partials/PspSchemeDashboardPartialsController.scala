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

package controllers.partials

import connectors.FinancialStatementConnector
import controllers.actions._
import models.financialStatement.SchemeFSDetail
import models.requests.IdentifierRequest
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import play.twirl.api.{Html, HtmlFormat}
import services.{AFTPartialService, SchemeService}
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.partials.SchemePaymentsAndChargesPartialView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PspSchemeDashboardPartialsController @Inject()(
                                                      identify: IdentifierAction,
                                                      override val messagesApi: MessagesApi,
                                                      val controllerComponents: MessagesControllerComponents,
                                                      schemeService: SchemeService,
                                                      financialStatementConnector: FinancialStatementConnector,
                                                      aftPartialService: AFTPartialService,
                                                      view: SchemePaymentsAndChargesPartialView
                                                    )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {
  // scalastyle:off
  def pspDashboardAllTilesPartial(): Action[AnyContent] = identify.async {
    implicit request =>
      val srn = request.headers.get("idNumber")
      val schemeIdType = request.headers.get("schemeIdType")
      val authorisingPsaId = request.headers.get("authorisingPsaId")

      (srn, schemeIdType, authorisingPsaId) match {
        case (Some(srn), Some(_), Some(psaId)) =>
              val futureSeqHtml = for {
                schemeDetails <- schemeService.retrieveSchemeDetails(request.idOrException, srn)
                schemeFs <- financialStatementConnector.getSchemeFS(schemeDetails.pstr, srn, request.isLoggedInAsPsa)
                paymentsAndChargesHtml <- pspDashboardPaymentsAndChargesPartial(srn, schemeFs.seqSchemeFSDetail, schemeDetails.pstr)
              }
              yield {
                scala.collection.immutable.Seq(paymentsAndChargesHtml)
              }
              futureSeqHtml.map(HtmlFormat.fill).map(Ok(_))
        case _ =>
          Future.failed(
            new BadRequestException("Bad Request with missing parameters idNumber, schemeIdType, psaId and/or authorisingPsaId")
          )
      }
  }


  private def pspDashboardPaymentsAndChargesPartial(idNumber: String, schemeFs: Seq[SchemeFSDetail], pstr: String)
                                                   (implicit request: IdentifierRequest[AnyContent]): Future[Html] = {
    if (schemeFs.isEmpty) {
      Future.successful(Html(""))
    } else {
      val viewModel = aftPartialService.retrievePspDashboardPaymentsAndChargesModel(schemeFs, idNumber, pstr)
      Future.successful(view(viewModel))
    }
  }
}
