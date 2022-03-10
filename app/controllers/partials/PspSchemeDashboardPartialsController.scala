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

package controllers.partials

import connectors.FinancialStatementConnector
import controllers.actions._
import models.FeatureToggle.{Disabled, Enabled}
import models.FeatureToggleName.FinancialInformationAFT
import models.financialStatement.SchemeFS
import models.requests.IdentifierRequest
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import play.twirl.api.{Html, HtmlFormat}
import renderer.Renderer
import services.paymentsAndCharges.PaymentsAndChargesService
import services.{AFTPartialService, FeatureToggleService, SchemeService}
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PspSchemeDashboardPartialsController @Inject()(
                                                      identify: IdentifierAction,
                                                      override val messagesApi: MessagesApi,
                                                      val controllerComponents: MessagesControllerComponents,
                                                      schemeService: SchemeService,
                                                      financialStatementConnector: FinancialStatementConnector,
                                                      paymentsAndChargesService: PaymentsAndChargesService,
                                                      aftPartialService: AFTPartialService,
                                                      toggleService: FeatureToggleService,
                                                      renderer: Renderer
                                                    )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def pspDashboardAllTilesPartial(): Action[AnyContent] = identify.async {
    implicit request =>
      val idNumber = request.headers.get("idNumber")
      val schemeIdType = request.headers.get("schemeIdType")
      val authorisingPsaId = request.headers.get("authorisingPsaId")

      (idNumber, schemeIdType, authorisingPsaId) match {
        case (Some(idNumber), Some(_), Some(psaId)) =>
          toggleService.get(FinancialInformationAFT).flatMap {
            case Disabled(FinancialInformationAFT) =>
              val futureSeqHtml = for {
                schemeDetails <- schemeService.retrieveSchemeDetails(request.idOrException, idNumber, "srn")
                schemeFs <- financialStatementConnector.getSchemeFS(schemeDetails.pstr)
                aftReturnsHtml <- pspDashboardAftReturnsPartial(idNumber, schemeDetails.pstr, psaId)
                upcomingAftChargesHtml <- pspDashboardUpcomingAftChargesPartial(idNumber, schemeFs)
                overdueChargesHtml <- pspDashboardOverdueAftChargesPartial(idNumber, schemeFs)
              } yield {
                scala.collection.immutable.Seq(aftReturnsHtml, upcomingAftChargesHtml, overdueChargesHtml)
              }

              futureSeqHtml.map(HtmlFormat.fill).map(Ok(_))

            case Enabled(FinancialInformationAFT) =>

              val futureSeqHtml = for {
                schemeDetails <- schemeService.retrieveSchemeDetails(request.idOrException, idNumber, "srn")
                schemeFs <- financialStatementConnector.getSchemeFS(schemeDetails.pstr)
                aftReturnsHtml <- pspDashboardAftReturnsPartial(idNumber, schemeDetails.pstr, psaId)
                paymentsAndChargesHtml <- pspDashboardPaymentsAndChargesPartial(idNumber, schemeFs)
              }
              yield {
                scala.collection.immutable.Seq(aftReturnsHtml, paymentsAndChargesHtml)
              }
              futureSeqHtml.map(HtmlFormat.fill).map(Ok(_))
          }
        case _ =>
          Future.failed(
            new BadRequestException("Bad Request with missing parameters idNumber, schemeIdType, psaId and/or authorisingPsaId")
          )
      }
  }

  private def pspDashboardAftReturnsPartial(idNumber: String, pstr: String, authorisingPsaId: String)(implicit
                                                                                                      request: IdentifierRequest[AnyContent], hc: HeaderCarrier): Future[Html] = {
    aftPartialService.retrievePspDashboardAftReturnsModel(idNumber, pstr, authorisingPsaId) flatMap {
      viewModel =>
        renderer.render(
          template = "partials/pspDashboardAftReturnsCard.njk",
          ctx = Json.obj("aft" -> Json.toJson(viewModel))
        )
    }
  }

  private def pspDashboardUpcomingAftChargesPartial(idNumber: String, schemeFs: Seq[SchemeFS])
                                                   (implicit request: IdentifierRequest[AnyContent]): Future[Html] = {
    if (schemeFs.isEmpty) {
      Future.successful(Html(""))
    } else {
      val viewModel =
        aftPartialService.retrievePspDashboardUpcomingAftChargesModel(schemeFs, idNumber)
      renderer.render(
        template = "partials/pspDashboardUpcomingAftChargesCard.njk",
        ctx = Json.obj("upcomingCharges" -> Json.toJson(viewModel))
      )
    }
  }

  private def pspDashboardPaymentsAndChargesPartial(idNumber: String, schemeFs: Seq[SchemeFS])
                                                   (implicit request: IdentifierRequest[AnyContent]): Future[Html] = {
    if (schemeFs.isEmpty) {
      Future.successful(Html(""))
    } else {
      val viewModel =
        aftPartialService.retrievePspDashboardPaymentsAndChargesModel(schemeFs, idNumber)
      renderer.render(
        template = "partials/pspSchemePaymentsAndChargesPartial.njk",
        ctx = Json.obj("cards" -> Json.toJson(viewModel))
      )
    }
  }

  private def pspDashboardOverdueAftChargesPartial(idNumber: String, schemeFs: Seq[SchemeFS])
                                                  (implicit request: IdentifierRequest[AnyContent]): Future[Html] = {
    val overdueCharges = paymentsAndChargesService.getOverdueCharges(schemeFs)
    if (overdueCharges.isEmpty) {
      Future.successful(Html(""))
    } else {
      val viewModel =
        aftPartialService.retrievePspDashboardOverdueAftChargesModel(overdueCharges, idNumber)
      renderer.render(
        template = "partials/pspDashboardOverdueAftChargesCard.njk",
        ctx = Json.obj("overdueCharges" -> Json.toJson(viewModel))
      )
    }
  }
}
