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
import models.requests.IdentifierRequest
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.financialOverview.psa.{PenaltiesCache, PsaPenaltiesAndChargesService}
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.Table
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.financialOverview.psa.PsaPaymentsAndChargesView

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
                                                 config: FrontendAppConfig,
                                                 view: PsaPaymentsAndChargesView
                                               )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  private val logger = Logger(classOf[PsaPaymentsAndChargesController])

  def onPageLoad(journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess()).async { implicit request: IdentifierRequest[AnyContent] =>
      val response = for {
        psaName                   <- minimalConnector.getPsaOrPspName
        psaFSWithPaymentOnAccount <- financialStatementConnector.getPsaFSWithPaymentOnAccount(request.psaIdOrException.id)
        penaltiesCache            <- psaPenaltiesAndChargesService.getPenaltiesForJourney(request.psaIdOrException.id, journeyType)
      } yield {
        renderFinancialOverdueAndInterestCharges(psaName, request.psaIdOrException.id, psaFSWithPaymentOnAccount.seqPsaFSDetail, journeyType, penaltiesCache)
      }
      response.flatten
    }

  private def renderFinancialOverdueAndInterestCharges(psaName: String,
                                                       psaId: String,
                                                       creditPsaFS: Seq[PsaFSDetail],
                                                       journeyType: ChargeDetailsFilter,
                                                       penaltiesCache: PenaltiesCache)
                                                      (implicit request: IdentifierRequest[AnyContent]): Future[Result] = {

    val psaCharges = psaPenaltiesAndChargesService.retrievePsaChargesAmount(creditPsaFS)

    logger.debug(s"AFT service returned UpcomingCharge - ${psaCharges.upcomingCharge}")
    logger.debug(s"AFT service returned OverdueCharge - ${psaCharges.overdueCharge}")
    logger.debug(s"AFT service returned InterestAccruing - ${psaCharges.interestAccruing}")

    psaPenaltiesAndChargesService.getPenaltiesAndCharges(psaId,
      penaltiesCache.penalties, journeyType, config) flatMap { table =>

      val penaltiesTable: Table = if (journeyType == Upcoming) {
        removePaymentStatusColumn(table)
      } else {
        table
      }

      val messages = request2Messages

      Future.successful(Ok(view(
        titleMessage           = messages(s"psa.financial.overview.$journeyType.title"), journeyType = journeyType,
        psaName                = psaName,
        reflectChargeText      = messages(s"psa.financial.overview.$journeyType.text"),
        totalOverdueCharge     = psaCharges.overdueCharge,
        totalInterestAccruing  = psaCharges.interestAccruing,
        totalUpcomingCharge    = psaCharges.upcomingCharge,
        totalOutstandingCharge = "",
        penaltiesTable         = penaltiesTable,
        paymentAndChargesTable = penaltiesTable
      )))
    }
  }

  private val removePaymentStatusColumn: Table => Table = table => {
    Table(
      caption            = table.caption,
      captionClasses     = table.captionClasses,
      firstCellIsHeader  = table.firstCellIsHeader,
      head               = Some(table.head.getOrElse(Seq()).take(table.head.size - 1)),
      rows               = table.rows.map(p => p.take(p.size - 1)),
      classes            = table.classes,
      attributes         = table.attributes
    )
  }
}
