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
import models.ChargeDetailsFilter.Upcoming
import models.financialStatement.{SchemeFSChargeType, SchemeFSDetail}
import models.{ChargeDetailsFilter, UserAnswers}
import pages.{AFTReceiptDateQuery, AFTVersionQuery}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import renderer.Renderer
import services.financialOverview.scheme.PaymentsAndChargesService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import uk.gov.hmrc.viewmodels.Text.Message
import viewmodels.Table

import java.time.LocalDate
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

  // scalastyle:off method.length
  def onPageLoad(srn: String, pstr: String, journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess()).async { implicit request =>
      paymentsAndChargesService.getPaymentsForJourney(request.idOrException, srn, journeyType).flatMap { paymentsCache =>
        val overdueCharges: Seq[SchemeFSDetail] = paymentsAndChargesService.getOverdueCharges(paymentsCache.schemeFSDetail)
        val interestCharges: Seq[SchemeFSDetail] = paymentsAndChargesService.getInterestCharges(paymentsCache.schemeFSDetail)
        val totalOverdue: BigDecimal = overdueCharges.map(_.amountDue).sum
        val totalInterestAccruing: BigDecimal = interestCharges.map(_.accruedInterestTotal).sum
        val upcomingCharges: Seq[SchemeFSDetail] = paymentsAndChargesService.extractUpcomingCharges(paymentsCache.schemeFSDetail)
        val totalUpcoming: BigDecimal = upcomingCharges.map(_.amountDue).sum

        if (paymentsCache.schemeFSDetail.nonEmpty) {
          paymentsAndChargesService.getPaymentsAndCharges(srn, pstr, paymentsCache.schemeFSDetail, journeyType).flatMap { table =>
            val tableOfPaymentsAndCharges = if (journeyType == Upcoming) removePaymentStatusColumn(table) else table
            val json = Json.obj(
              fields =
                "titleMessage" -> Message(s"financialPaymentsAndCharges.$journeyType.title"),
              "reflectChargeText" -> Message(s"financialPaymentsAndCharges.$journeyType.reflect.charge.text"),
              "journeyType" -> journeyType.toString,
              "paymentAndChargesTable" -> tableOfPaymentsAndCharges,
              "schemeName" -> paymentsCache.schemeDetails.schemeName,
              "totalOverdue" -> s"${FormatHelper.formatCurrencyAmountAsString(totalOverdue)}",
              "totalInterestAccruing" -> s"${FormatHelper.formatCurrencyAmountAsString(totalInterestAccruing)}",
              "totalUpcoming" -> s"${FormatHelper.formatCurrencyAmountAsString(totalUpcoming)}",
              "returnUrl" -> config.schemeDashboardUrl(request).format(srn)
            )
            renderer.render(template = "financialOverview/paymentsAndCharges.njk", json).map(Ok(_))
          }
        } else {
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
        }
      }
    }

  private val removePaymentStatusColumn: Table => Table = table =>
    Table(table.caption, table.captionClasses, table.firstCellIsHeader,
      table.head.take(table.head.size - 1),
      table.rows.map(p => p.take(p.size - 1)), table.classes, table.attributes
    )
}
