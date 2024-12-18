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
import connectors.{FinancialStatementConnector, MinimalConnector}
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, IdentifierAction}
import models.ChargeDetailsFilter
import models.ChargeDetailsFilter.Upcoming
import models.financialStatement.PsaFSDetail
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc._
import renderer.Renderer
import services.financialOverview.psa.{PenaltiesCache, PsaPenaltiesAndChargesService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import uk.gov.hmrc.viewmodels.Text.Message
import viewmodels.Table

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PsaPaymentsAndChargesController @Inject()(
                                                 override val messagesApi: MessagesApi,
                                                 identify: IdentifierAction,
                                                 allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                                 val controllerComponents: MessagesControllerComponents,
                                                 psaPenaltiesAndChargesService: PsaPenaltiesAndChargesService,
                                                 financialStatementConnector: FinancialStatementConnector,
                                                 minimalConnector: MinimalConnector,
                                                 renderer: Renderer,
                                                 config: FrontendAppConfig
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
        renderFinancialOverdueAndInterestCharges(psaName, request.psaIdOrException.id,
          request, psaFSWithPaymentOnAccount.seqPsaFSDetail, journeyType, penaltiesCache)
      }
      response.flatten
    }

  //scalastyle:off parameter.number
  //scalastyle:off method.length
  //scalastyle:off cyclomatic.complexity
  private def renderFinancialOverdueAndInterestCharges(psaName: String,
                                                       psaId: String,
                                                       request: RequestHeader,
                                                       creditPsaFS: Seq[PsaFSDetail],
                                                       journeyType: ChargeDetailsFilter,
                                                       penaltiesCache: PenaltiesCache)
                                                      (implicit messages: Messages, headerCarrier: HeaderCarrier): Future[Result] = {

    val psaCharges = psaPenaltiesAndChargesService.retrievePsaChargesAmount(creditPsaFS)


    logger.debug(s"AFT service returned UpcomingCharge - ${psaCharges.upcomingCharge}")
    logger.debug(s"AFT service returned OverdueCharge - ${psaCharges.overdueCharge}")
    logger.debug(s"AFT service returned InterestAccruing - ${psaCharges.interestAccruing}")

    psaPenaltiesAndChargesService.getPenaltiesAndCharges(psaId,
      penaltiesCache.penalties, journeyType, config) flatMap { table =>

      val penaltiesTable = if (journeyType == Upcoming) {
        removePaymentStatusColumn(table)
      } else {
        table
      }
      val psaPaymentsAndChargesTemplate = if(config.podsNewFinancialCredits) {
        "financialOverview/psa/psaPaymentsAndChargesNew.njk"
      } else {
        "financialOverview/psa/psaPaymentsAndCharges.njk"
      }

      val reflectChargeText = if(config.podsNewFinancialCredits) {
        s"psa.financial.overview.$journeyType.text.new"
      } else {
        s"psa.financial.overview.$journeyType.text"
      }

      renderer.render(
        template = psaPaymentsAndChargesTemplate,
        ctx = Json.obj("totalUpcomingCharge" -> psaCharges.upcomingCharge,
          "totalOverdueCharge" -> psaCharges.overdueCharge,
          "totalInterestAccruing" -> psaCharges.interestAccruing,
          "titleMessage" -> getTitleMessage(journeyType),
          "reflectChargeText" -> getReflectChargeText(journeyType),
          "journeyType" -> journeyType.toString,
          "penaltiesTable" -> penaltiesTable,
          "psaName" -> psaName,
          "podsNewFinancialCredits" -> config.podsNewFinancialCredits)
      )(request).map(Ok(_))
    }
  }

  private def getTitleMessage(journeyType: ChargeDetailsFilter) = {
    if (config.podsNewFinancialCredits) {
      Message(s"psa.financial.overview.$journeyType.title.v2")
    } else {
      Message(s"psa.financial.overview.$journeyType.title")
    }
  }

  private def getReflectChargeText(journeyType: ChargeDetailsFilter) = {
    if (config.podsNewFinancialCredits) {
      Message(s"psa.financial.overview.$journeyType.text.v2")
    } else {
      Message(s"psa.financial.overview.$journeyType.text")
    }
  }

  private val removePaymentStatusColumn: Table => Table = table =>
    Table(table.caption, table.captionClasses, table.firstCellIsHeader,
      table.head.take(table.head.size - 1),
      table.rows.map(p => p.take(p.size - 1)), table.classes, table.attributes
    )
}

