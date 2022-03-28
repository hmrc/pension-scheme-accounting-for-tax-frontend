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
import models.FeatureToggle._
import models.FeatureToggleName._
import models.SchemeDetails
import models.requests.IdentifierRequest
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.{FeatureToggleService, PsaSchemePartialService, SchemeService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import viewmodels.CardViewModel

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PsaSchemeDashboardPartialsController @Inject()(
    identify: IdentifierAction,
    override val messagesApi: MessagesApi,
    val controllerComponents: MessagesControllerComponents,
    schemeService: SchemeService,
    financialStatementConnector: FinancialStatementConnector,
    service: PsaSchemePartialService,
    toggleService: FeatureToggleService,
    renderer: Renderer
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val logger = Logger(classOf[PsaSchemeDashboardPartialsController])


  def psaSchemeDashboardPartial(srn: String): Action[AnyContent] = identify.async {
    implicit request =>
      toggleService.get(FinancialInformationAFT).flatMap {
        case Enabled(FinancialInformationAFT) =>
          schemeService.retrieveSchemeDetails(request.idOrException, srn, "srn").flatMap { schemeDetails =>
            val aftModel =  service.aftCardModel(schemeDetails, srn)
            val financialOverviewModel=getFinancialOverviewTile(srn, schemeDetails)
            val cardsSequence = Future.traverse(Seq(aftModel,financialOverviewModel)) { cards =>
              cards.recover {
                case ex =>
                  logger.warn("Error in financialOverview or aftModel tiles", ex)
                  Nil
              }
            }
            val mergeCards = cardsSequence.map(_.flatten)
            mergeCards.flatMap { cards =>
              renderer
                .render(
                  template = "partials/psaSchemeDashboardPartial.njk",
                  ctx = Json.obj("cards" -> Json.toJson(cards))
                )
                .map(Ok(_))
            }
        }
      case Disabled(FinancialInformationAFT) =>
        schemeService.retrieveSchemeDetails(request.idOrException, srn, "srn").flatMap { schemeDetails =>
          val aftModel = service.aftCardModel(schemeDetails, srn)
          val upcomingAndOverdueModel = getUpcomingAndOverdueTile(srn, schemeDetails)
          val cardsSequence = Future.traverse(Seq(aftModel,upcomingAndOverdueModel)) { cards =>
            cards.recover {
              case ex =>
                logger.warn("Error in Upcoming or aftModel tiles", ex)
                Nil
            }
          }
          val mergeCards = cardsSequence.map(_.flatten)
          mergeCards.flatMap { cards =>
            renderer
              .render(
                template = "partials/psaSchemeDashboardPartial.njk",
                ctx = Json.obj("cards" -> Json.toJson(cards))
              )
              .map(Ok(_))
          }
        }
    }
  }

  private def getUpcomingAndOverdueTile(srn: String, schemeDetails: SchemeDetails)(implicit request: IdentifierRequest[AnyContent]) = {
    financialStatementConnector.getSchemeFS(schemeDetails.pstr).map { schemeFSDetail =>
      val upcomingTile: Seq[CardViewModel] = service.upcomingAftChargesModel(schemeFSDetail.seqSchemeFSDetail, srn)
      val overdueTile: Seq[CardViewModel] = service.overdueAftChargesModel(schemeFSDetail.seqSchemeFSDetail, srn)
      logger.debug(s"AFT service returned partial for psa scheme dashboard with aft tile- ${Json.toJson(upcomingTile)}")
      logger.debug(s"AFT service returned partial for psa scheme dashboard with aft tile- ${Json.toJson(overdueTile)}")
      upcomingTile ++ overdueTile
    }
  }
  private def getFinancialOverviewTile(srn: String, schemeDetails: SchemeDetails)(implicit request: IdentifierRequest[AnyContent]) = {
    financialStatementConnector.getSchemeFS(schemeDetails.pstr).map { schemeFSDetail =>
      val paymentsAndCharges: Seq[CardViewModel] = service.paymentsAndCharges(schemeFSDetail.seqSchemeFSDetail, srn, schemeDetails.pstr)
      logger.debug(s"AFT service returned partial for psa scheme dashboard with aft tile- ${Json.toJson(paymentsAndCharges)}")
      paymentsAndCharges
    }
  }
}
