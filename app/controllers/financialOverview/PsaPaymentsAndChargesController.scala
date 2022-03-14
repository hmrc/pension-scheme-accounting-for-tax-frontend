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

package controllers.financialOverview

import config.FrontendAppConfig
import connectors.{FinancialStatementConnector, MinimalConnector}
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, IdentifierAction}
import models.ChargeDetailsFilter
import models.financialStatement.{PsaFS, PsaFSChargeType}
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc._
import renderer.Renderer
import services.financialOverview.PsaPenaltiesAndChargesService
import services.{PenaltiesCache, SchemeService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import uk.gov.hmrc.viewmodels.Text.Message

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PsaPaymentsAndChargesController @Inject()(
                                                 override val messagesApi: MessagesApi,
                                                 identify: IdentifierAction,
                                                 allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                                 val controllerComponents: MessagesControllerComponents,
                                                 config: FrontendAppConfig,
                                                 psaPenaltiesAndChargesService: PsaPenaltiesAndChargesService,
                                                 schemeService: SchemeService,
                                                 financialStatementConnector: FinancialStatementConnector,
                                                 minimalConnector: MinimalConnector,
                                                 renderer: Renderer
                                               )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val logger = Logger(classOf[PsaPaymentsAndChargesController])

  def onPageLoad(journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess()).async { implicit request =>
        val response = for {
          psaName <- minimalConnector.getPsaOrPspName
          psaFSWithPaymentOnAccount <- financialStatementConnector.getPsaFSWithPaymentOnAccount(request.psaIdOrException.id)
          penaltiesCache <- psaPenaltiesAndChargesService.getPenaltiesForJourney(request.psaIdOrException.id, journeyType)
        } yield {
          val psaFSWithoutPaymentOnAccount: Seq[PsaFS] = psaFSWithPaymentOnAccount.filterNot(_.chargeType == PsaFSChargeType.PAYMENT_ON_ACCOUNT)
          renderFinancialOverdueAndInterestCharges(psaName, request.psaIdOrException.id, psaFSWithoutPaymentOnAccount,
            request, psaFSWithPaymentOnAccount, journeyType, penaltiesCache)
        }
        response.flatten
    }

  //scalastyle:off parameter.number
  // scalastyle:off method.length
  //scalastyle:off cyclomatic.complexity
  private def renderFinancialOverdueAndInterestCharges(psaName: String,
                                                       psaId: String,
                                                       psaFS: Seq[PsaFS],
                                                       request: RequestHeader,
                                                       creditPsaFS: Seq[PsaFS],
                                                       journeyType: ChargeDetailsFilter,
                                                       penaltiesCache: PenaltiesCache)
                                                      (implicit messages: Messages, headerCarrier: HeaderCarrier) : Future[Result] = {

    val psaCharges:(String,String,String) = psaPenaltiesAndChargesService.retrievePsaChargesAmount(psaFS)

    logger.debug(s"AFT service returned UpcomingCharge - ${psaCharges._1}")
    logger.debug(s"AFT service returned OverdueCharge - ${psaCharges._2}")
    logger.debug(s"AFT service returned InterestAccruing - ${psaCharges._3}")

    val chargeRefsIndex: String => String = cr => penaltiesCache.penalties.map(_.chargeReference).indexOf(cr).toString

    val table = psaPenaltiesAndChargesService.getAllPaymentsAndCharges(psaId, "", chargeRefsIndex, psaFS, journeyType)

    renderer.render(
      template = "financialOverview/psaPaymentsAndCharges.njk",
      ctx = Json.obj("totalUpcomingCharge" -> psaCharges._1 ,
        "totalOverdueCharge" -> psaCharges._2 ,
        "totalInterestAccruing" -> psaCharges._3,
        "titleMessage" -> Message(s"psa.financial.overview.$journeyType.title"),
        "reflectChargeText" -> Message(s"psa.financial.overview.reflect.text"),
        "journeyType" -> journeyType.toString,
        "penaltiesTable" -> table,
      "psaName" -> psaName)
    )(request).map(Ok(_))
  }

}

