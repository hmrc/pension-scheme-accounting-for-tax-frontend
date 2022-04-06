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

package controllers.financialOverview.psa

import connectors.{FinancialStatementConnector, MinimalConnector}
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, IdentifierAction}
import models.ChargeDetailsFilter
import models.ChargeDetailsFilter.Upcoming
import models.financialStatement.PsaFSChargeType.REPAYMENT_INTEREST
import models.financialStatement.{PsaFSChargeType, PsaFSDetail}
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc._
import renderer.Renderer
import services.financialOverview.psa.PenaltiesCache
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
          val psaFSWithoutPaymentOnAccount: Seq[PsaFSDetail] = psaFSWithPaymentOnAccount.seqPsaFSDetail.filterNot(c => c.chargeType ==
            PsaFSChargeType.PAYMENT_ON_ACCOUNT || c.chargeType == REPAYMENT_INTEREST)
          renderFinancialOverdueAndInterestCharges(psaName, request.psaIdOrException.id, psaFSWithoutPaymentOnAccount,
            request, psaFSWithPaymentOnAccount.seqPsaFSDetail, journeyType, penaltiesCache)
        }
        response.flatten
    }

  //scalastyle:off parameter.number
  //scalastyle:off method.length
  //scalastyle:off cyclomatic.complexity
  private def renderFinancialOverdueAndInterestCharges(psaName: String,
                                                       psaId: String,
                                                       psaFS: Seq[PsaFSDetail],
                                                       request: RequestHeader,
                                                       creditPsaFS: Seq[PsaFSDetail],
                                                       journeyType: ChargeDetailsFilter,
                                                       penaltiesCache: PenaltiesCache)
                                                      (implicit messages: Messages, headerCarrier: HeaderCarrier): Future[Result] = {

    val psaCharges: (String, String, String) = psaPenaltiesAndChargesService.retrievePsaChargesAmount(creditPsaFS)


    logger.debug(s"AFT service returned UpcomingCharge - ${psaCharges._1}")
    logger.debug(s"AFT service returned OverdueCharge - ${psaCharges._2}")
    logger.debug(s"AFT service returned InterestAccruing - ${psaCharges._3}")

    psaPenaltiesAndChargesService.getPenaltiesAndCharges(psaId,
      penaltiesCache.penalties, journeyType) flatMap { table =>

      val penaltiesTable = if (journeyType == Upcoming) {
        removePaymentStatusColumn(table)
      } else {
        table
      }

      renderer.render(
        template = "financialOverview/psa/psaPaymentsAndCharges.njk",
        ctx = Json.obj("totalUpcomingCharge" -> psaCharges._1,
          "totalOverdueCharge" -> psaCharges._2,
          "totalInterestAccruing" -> psaCharges._3,
          "titleMessage" -> Message(s"psa.financial.overview.$journeyType.title"),
          "reflectChargeText" -> Message(s"psa.financial.overview.$journeyType.text"),
          "journeyType" -> journeyType.toString,
          "penaltiesTable" -> penaltiesTable,
          "psaName" -> psaName)
      )(request).map(Ok(_))
    }
  }

  private val removePaymentStatusColumn: Table => Table = table =>
    Table(table.caption, table.captionClasses, table.firstCellIsHeader,
      table.head.take(table.head.size - 1),
      table.rows.map(p => p.take(p.size - 1)), table.classes, table.attributes
    )
}

