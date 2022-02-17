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
import connectors.AFTConnector
import controllers.actions._
import helpers.FormatHelper
import models.ChargeDetailsFilter.Upcoming
import models.financialStatement.PaymentOrChargeType.{ExcessReliefPaidCharges, InterestOnExcessRelief}
import models.financialStatement.{PaymentOrChargeType, SchemeFS}
import models.{ChargeDetailsFilter, UserAnswers}
import pages.AFTVersionQuery
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import renderer.Renderer
import services.paymentsAndCharges.PaymentsAndChargesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import uk.gov.hmrc.viewmodels.Text.Message
import viewmodels.Table

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PaymentsAndChargesController @Inject()(
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

  private val logger = Logger(classOf[PaymentsAndChargesController])

  def onPageLoad(srn: String, pstr: String, journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess()).async { implicit request =>
      paymentsAndChargesService.getPaymentsForJourney(request.idOrException, srn, journeyType).flatMap { paymentsCache =>
        val overdueCharges: Seq[SchemeFS] = paymentsAndChargesService.getOverdueCharges(paymentsCache.schemeFS)
        val totalOverdue: BigDecimal = overdueCharges.map(_.amountDue).sum
        val totalInterestAccruing: BigDecimal = overdueCharges.map(_.accruedInterestTotal).sum
              if (paymentsCache.schemeFS.nonEmpty)
              {
                val x = paymentsCache.schemeFS.map { scheme =>
                  aftConnector
                    .getAFTDetailsWithFbNumber(pstr, scheme.formBundleNumber.getOrElse(""))
                    .map { aftDetails =>
                      val ua = UserAnswers(aftDetails.as[JsObject])
                      Tuple2(ua.get(AFTVersionQuery).getOrElse(0), scheme.chargeType)
                    }
                }
                val y = Future.sequence(x)
                  y.map { aftVersionTuple =>
                    val table = paymentsAndChargesService.getPaymentsAndChargesNew(srn, paymentsCache.schemeFS, aftVersionTuple, journeyType)
                    if (journeyType == Upcoming) removePaymentStatusColumn(table) else table
                }.flatMap { tableOfPaymentsAndCharges =>
                  val json = Json.obj(
                    fields =
                      "titleMessage" -> Message("financialPaymentsAndCharges.title"),
                    "paymentAndChargesTable" -> tableOfPaymentsAndCharges,
                    "schemeName" -> paymentsCache.schemeDetails.schemeName,
                    "totalOverdue" -> s"${FormatHelper.formatCurrencyAmountAsString(totalOverdue)}",
                    "totalInterestAccruing" -> s"${FormatHelper.formatCurrencyAmountAsString(totalInterestAccruing)}",
                    "returnUrl" -> config.schemeDashboardUrl(request).format(srn)
                  )
                  renderer.render(template = "financialOverview/paymentsAndCharges.njk", json).map(Ok(_))

                }
              } else {
                Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
              }

      }
  }

  val isTaxYearFormat: PaymentOrChargeType => Boolean = ct => ct == InterestOnExcessRelief || ct == ExcessReliefPaidCharges


  private val removePaymentStatusColumn: Table => Table = table =>
    Table(table.caption, table.captionClasses, table.firstCellIsHeader,
      table.head.take(table.head.size - 1),
      table.rows.map(p => p.take(p.size - 1)), table.classes, table.attributes
    )
}
