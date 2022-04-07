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

import controllers.actions._
import helpers.FormatHelper
import models.ChargeDetailsFilter.All
import models.financialStatement.PenaltyType.{AccountingForTaxPenalties, getPenaltyType}
import models.financialStatement.{PenaltyType, PsaFSDetail}
import models.{ChargeDetailsFilter, Quarters}
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc._
import renderer.Renderer
import services.financialOverview.psa.PsaPenaltiesAndChargesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import uk.gov.hmrc.viewmodels.Text.Message
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AllPenaltiesAndChargesController @Inject()(
                                              override val messagesApi: MessagesApi,
                                              identify: IdentifierAction,
                                              allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                              val controllerComponents: MessagesControllerComponents,
                                              psaPenaltiesAndChargesService: PsaPenaltiesAndChargesService,
                                              renderer: Renderer
                                            )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val logger = Logger(classOf[AllPenaltiesAndChargesController])

  def onPageLoadAFT(startDate: LocalDate, pstr: String, penaltyType: PenaltyType, journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess()).async { implicit request =>

      val title: Message = Message("penalties.aft.title").withArgs(
        startDate.format(dateFormatterStartDate),
        Quarters.getQuarter(startDate).endDate.format(dateFormatterDMY))

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

        if (filteredPenalties.nonEmpty) {

          psaPenaltiesAndChargesService.getAllPenaltiesAndCharges(
            request.idOrException, filteredPenalties, All) flatMap { table =>

            val json = Json.obj(
              fields =
                "titleMessage" -> title,
              "reflectChargeText" -> Message(s"paymentsAndCharges.reflect.charge.text"),
              "journeyType" -> journeyType.toString,
              "paymentAndChargesTable" -> table,
              "totalOutstandingCharge" -> s"${FormatHelper.formatCurrencyAmountAsString(totalCharges)}",
              "pstr" -> pstr,
              "psaName" -> penaltiesCache.psaName
            )
            renderer.render(template = "financialOverview/psa/psaPaymentsAndCharges.njk", json).map(Ok(_))
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

        val title: Message = Message("penalties.nonAft.title", Message(s"penaltyType.${penaltyType.toString}"), year)
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

            val json = Json.obj(
              fields =
                "titleMessage" -> title,
              "reflectChargeText" -> Message(s"paymentsAndCharges.reflect.charge.text"),
              "journeyType" -> journeyType.toString,
              "paymentAndChargesTable" -> table,
              "totalOutstandingCharge" -> s"${FormatHelper.formatCurrencyAmountAsString(totalCharges)}",
              "pstr" -> pstr,
              "psaName" -> penaltiesCache.psaName
            )
            renderer.render(template = "financialOverview/psa/psaPaymentsAndCharges.njk", json).map(Ok(_))
          }
        } else {
          logger.warn(s"No Scheme Payments and Charges returned for the selected period $year")
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
        }
      }
    }
}
