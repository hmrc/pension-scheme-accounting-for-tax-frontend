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

import controllers.actions._
import helpers.FormatHelper
import models.ChargeDetailsFilter.{All, Upcoming}
import models.financialStatement.PenaltyType.{AccountingForTaxPenalties, getPenaltyType}
import models.financialStatement.{PenaltyType, PsaFSDetail}
import models.{ChargeDetailsFilter, Quarters}
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.financialOverview.psa.PsaPenaltiesAndChargesService
import uk.gov.hmrc.govukfrontend.views.Aliases.Table
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}
import views.html.financialOverview.psa.PsaPaymentsAndChargesNewView

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AllPenaltiesAndChargesController @Inject()(
                                                  override val messagesApi: MessagesApi,
                                                  identify: IdentifierAction,
                                                  allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                                  val controllerComponents: MessagesControllerComponents,
                                                  psaPenaltiesAndChargesService: PsaPenaltiesAndChargesService,
                                                  view: PsaPaymentsAndChargesNewView
                                                )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  private val logger = Logger(classOf[AllPenaltiesAndChargesController])

  def onPageLoadAFT(startDate: LocalDate, pstr: String, penaltyType: PenaltyType, journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess()).async { implicit request =>

      psaPenaltiesAndChargesService.getPenaltiesForJourney(request.idOrException, journeyType).flatMap { penaltiesCache =>

        val filteredPenalties: Seq[PsaFSDetail] = penaltiesCache.penalties
          .filter(_.periodStartDate == startDate)
          .filter(_.pstr == pstr)
          .filter(p => getPenaltyType(p.chargeType) == AccountingForTaxPenalties)
        val dueCharges: Seq[PsaFSDetail] = psaPenaltiesAndChargesService.getDueCharges(filteredPenalties)
        val totalDueCharges: BigDecimal = dueCharges.map(_.amountDue).sum
        val interestCharges: Seq[PsaFSDetail] = psaPenaltiesAndChargesService.getInterestCharges(filteredPenalties)
        val totalInterestCharges: BigDecimal = interestCharges.map(_.accruedInterestTotal).sum
        val totalCharges: BigDecimal = totalDueCharges + totalInterestCharges
        val messages = request2Messages

        val title = messages("penalties.aft.title",
          startDate.format(dateFormatterStartDate),
          Quarters.getQuarter(startDate).endDate.format(dateFormatterDMY))

        if (filteredPenalties.nonEmpty) {

          psaPenaltiesAndChargesService.getAllPenaltiesAndCharges(
            request.idOrException, filteredPenalties, All) flatMap { table =>

            val penaltiesTable: Table = if (journeyType == Upcoming) {
              removePaymentStatusColumn(table)
            } else {
              table
            }

            Future.successful(Ok(view(
              journeyType = journeyType,
              psaName = penaltiesCache.psaName,
              titleMessage = title,
              pstr = Some(pstr),
              reflectChargeText = messages(s"paymentsAndCharges.reflect.charge.text"),
              totalOverdueCharge = s"${FormatHelper.formatCurrencyAmountAsString(totalCharges)}",
              totalInterestAccruing = s"${FormatHelper.formatCurrencyAmountAsString(totalInterestCharges)}",
              totalUpcomingCharge = s"${FormatHelper.formatCurrencyAmountAsString(totalDueCharges)}",
              totalOutstandingCharge = s"${FormatHelper.formatCurrencyAmountAsString(totalCharges)}",
              penaltiesTable = penaltiesTable,
              paymentAndChargesTable = penaltiesTable,
            )))
          }
        } else {
          logger.warn(s"No Scheme Payments and Charges returned for the selected period $startDate")
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
        }
      }
    }

  def onPageLoad(year: String, pstr: String, penaltyType: PenaltyType, journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess()).async { implicit request =>

      psaPenaltiesAndChargesService.getPenaltiesForJourney(request.idOrException, journeyType).flatMap { penaltiesCache =>
        val messages = request2Messages

        val title = messages("penalties.nonAft.title", messages(s"penaltyType.${penaltyType.toString}"), year)
        val filteredPenalties: Seq[PsaFSDetail] = penaltiesCache.penalties
          .filter(_.periodEndDate.getYear == year.toInt)
          .filter(_.pstr == pstr)
          .filter(p => getPenaltyType(p.chargeType) == penaltyType)

        val dueCharges: Seq[PsaFSDetail] = psaPenaltiesAndChargesService.getDueCharges(filteredPenalties)
        val totalDueCharges: BigDecimal = dueCharges.map(_.amountDue).sum
        val interestCharges: Seq[PsaFSDetail] = psaPenaltiesAndChargesService.getInterestCharges(filteredPenalties)
        val totalInterestCharges: BigDecimal = interestCharges.map(_.accruedInterestTotal).sum
        val totalCharges: BigDecimal = totalDueCharges + totalInterestCharges

        if (filteredPenalties.nonEmpty) {

          psaPenaltiesAndChargesService.getAllPenaltiesAndCharges(
            request.idOrException, filteredPenalties, All) flatMap { table =>

            val penaltiesTable: Table = if (journeyType == Upcoming) {
              removePaymentStatusColumn(table)
            } else {
              table
            }

            Future.successful(Ok(view(
              journeyType = journeyType.toString,
              psaName = penaltiesCache.psaName,
              titleMessage = title,
              pstr = Some(pstr),
              reflectChargeText = messages(s"paymentsAndCharges.reflect.charge.text"),
              totalOverdueCharge = s"${FormatHelper.formatCurrencyAmountAsString(totalCharges)}",
              totalInterestAccruing = s"${FormatHelper.formatCurrencyAmountAsString(totalInterestCharges)}",
              totalUpcomingCharge = s"${FormatHelper.formatCurrencyAmountAsString(totalDueCharges)}",
              totalOutstandingCharge = s"${FormatHelper.formatCurrencyAmountAsString(totalCharges)}",
              penaltiesTable = penaltiesTable,
              paymentAndChargesTable = penaltiesTable,
            )))
          }
        } else {
          logger.warn(s"No Scheme Payments and Charges returned for the selected period $year")
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
        }
      }
    }

  private val removePaymentStatusColumn: Table => Table = table => {
    Table(caption = table.caption,
      captionClasses = table.captionClasses,
      firstCellIsHeader = table.firstCellIsHeader,
      head = Some(table.head.getOrElse(Seq()).take(table.head.size - 1)),
      rows = table.rows.map(p => p.take(p.size - 1)),
      classes = table.classes,
      attributes = table.attributes
    )
  }
}
