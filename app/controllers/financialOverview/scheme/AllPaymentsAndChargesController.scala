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

package controllers.financialOverview.scheme

import config.FrontendAppConfig
import connectors.AFTConnector
import controllers.actions._
import helpers.FormatHelper
import models.financialStatement.PaymentOrChargeType.{AccountingForTaxCharges, ExcessReliefPaidCharges, InterestOnExcessRelief, getPaymentOrChargeType}
import models.financialStatement.{PaymentOrChargeType, SchemeFSChargeType, SchemeFSDetail}
import models.{ChargeDetailsFilter, Quarters, UserAnswers}
import pages.{AFTReceiptDateQuery, AFTVersionQuery}
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import renderer.Renderer
import services.financialOverview.scheme.PaymentsAndChargesService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import uk.gov.hmrc.viewmodels.Text.Message
import utils.DateHelper
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AllPaymentsAndChargesController @Inject()(
                                              override val messagesApi: MessagesApi,
                                              identify: IdentifierAction,
                                              allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                              val controllerComponents: MessagesControllerComponents,
                                              config: FrontendAppConfig,
                                              paymentsAndChargesService: PaymentsAndChargesService,
                                              aftConnector: AFTConnector,
                                              renderer: Renderer
                                            )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val logger = Logger(classOf[AllPaymentsAndChargesController])

  private def getMapChargeTypeToVersionAndDate(seqSchemeFS: Seq[SchemeFSDetail], pstr: String)(implicit ec: ExecutionContext,
                                                                                               headerCarrier: HeaderCarrier): Future[Map[SchemeFSChargeType, (Option[Int], Option[LocalDate])]] = {
    val tuple = Future.sequence {
      seqSchemeFS.map { scheme =>
        scheme.formBundleNumber match {
          case Some(fb) => aftConnector
            .getAFTDetailsWithFbNumber(pstr, fb)
            .map { aftDetails =>
              val ua = UserAnswers(aftDetails.as[JsObject])
              Seq(Tuple2(scheme.chargeType, (ua.get(AFTVersionQuery), ua.get(AFTReceiptDateQuery))))
            }
          case None => Future.successful(Nil)
        }
      }
    }.map(_.flatten)
    tuple.map(_.toMap)
  }

  def onPageLoad(srn: String, pstr: String, period: String, paymentOrChargeType: PaymentOrChargeType, journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess()).async { implicit request =>
      paymentsAndChargesService.getPaymentsForJourney(request.idOrException, srn, journeyType).flatMap { paymentsCache =>

              val (title, filteredPayments): (String, Seq[SchemeFSDetail]) =
                getTitleAndFilteredPayments(paymentsCache.schemeFSDetail, period, paymentOrChargeType)

        val dueCharges: Seq[SchemeFSDetail] = paymentsAndChargesService.getDueCharges(filteredPayments)
        val totalDueCharges: BigDecimal = dueCharges.map(_.amountDue).sum
        val interestCharges: Seq[SchemeFSDetail] = paymentsAndChargesService.getInterestCharges(filteredPayments)
        val totalInterestCharges: BigDecimal = interestCharges.map(_.accruedInterestTotal).sum
        val totalCharges: BigDecimal = totalDueCharges+ totalInterestCharges

          if (filteredPayments.nonEmpty) {

                getMapChargeTypeToVersionAndDate(filteredPayments, pstr).flatMap { mapChargeTypesVersionsAndDate =>
                  val tableOfPaymentsAndCharges = paymentsAndChargesService.getPaymentsAndCharges(srn, pstr, filteredPayments,
                    mapChargeTypesVersionsAndDate, journeyType)

                  val json = Json.obj(
                    fields =
                      "titleMessage" -> title,
                      "reflectChargeText" -> Message(s"paymentsAndCharges.reflect.charge.text"),
                      "journeyType" -> journeyType.toString,
                      "paymentAndChargesTable" -> tableOfPaymentsAndCharges,
                      "schemeName" -> paymentsCache.schemeDetails.schemeName,
                      "totalDue" -> s"${FormatHelper.formatCurrencyAmountAsString(totalCharges)}",
                      "returnUrl" -> config.schemeDashboardUrl(request).format(srn)
                  )
                  renderer.render(template = "financialOverview/paymentsAndCharges.njk", json).map(Ok(_))
                }
              } else {
                logger.warn(s"No Scheme Payments and Charges returned for the selected period $period")
                Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
              }

      }
  }

  val isTaxYearFormat: PaymentOrChargeType => Boolean = ct => ct == InterestOnExcessRelief || ct == ExcessReliefPaidCharges

  private def getTitleAndFilteredPayments(payments: Seq[SchemeFSDetail], period: String, paymentOrChargeType: PaymentOrChargeType)
                                         (implicit messages: Messages): (String, Seq[SchemeFSDetail]) =
    if(paymentOrChargeType == AccountingForTaxCharges) {

      val startDate: LocalDate = LocalDate.parse(period)
      (messages(s"paymentsAndCharges.all.aft.title",
        startDate.format(dateFormatterStartDate),
        Quarters.getQuarter(startDate).endDate.format(dateFormatterDMY)),
      payments.filter(p => getPaymentOrChargeType(p.chargeType) == AccountingForTaxCharges).filter(_.periodStartDate.contains(startDate)))

    } else {

      val typeParam: String = messages(s"paymentOrChargeType.${paymentOrChargeType.toString}")
      val filteredPayments = payments.filter(p => getPaymentOrChargeType(p.chargeType) == paymentOrChargeType)
        .filter(_.periodEndDate.exists(_.getYear == period.toInt))
      val title = if(isTaxYearFormat(paymentOrChargeType) && filteredPayments.nonEmpty) {
        messages(s"paymentsAndCharges.all.excessCharges.title", typeParam,
          DateHelper.formatDateDMY(filteredPayments.head.periodStartDate),
          DateHelper.formatDateDMY(filteredPayments.head.periodEndDate)
        )
      } else {
        messages(s"paymentsAndCharges.all.nonAft.title", typeParam, period)
      }
        (title, filteredPayments)
    }

}
