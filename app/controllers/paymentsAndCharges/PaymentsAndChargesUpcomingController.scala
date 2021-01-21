/*
 * Copyright 2021 HM Revenue & Customs
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

package controllers.paymentsAndCharges

import config.FrontendAppConfig
import connectors.FinancialStatementConnector
import controllers.actions._
import models.ChargeDetailsFilter
import models.financialStatement.SchemeFS
import models.viewModels.paymentsAndCharges.PaymentsAndChargesTable
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc._
import renderer.Renderer
import services.SchemeService
import services.paymentsAndCharges.PaymentsAndChargesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import viewmodels.Table

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PaymentsAndChargesUpcomingController @Inject()(
                                                      override val messagesApi: MessagesApi,
                                                      identify: IdentifierAction,
                                                      val controllerComponents: MessagesControllerComponents,
                                                      config: FrontendAppConfig,
                                                      schemeService: SchemeService,
                                                      fsConnector: FinancialStatementConnector,
                                                      paymentsAndChargesService: PaymentsAndChargesService,
                                                      renderer: Renderer
                                                    )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def onPageLoad(srn: String, startDate: LocalDate): Action[AnyContent] = identify.async {
    implicit request =>
      schemeService.retrieveSchemeDetails(
        psaId = request.idOrException,
        srn = srn,
        schemeIdType = "srn"
      ) flatMap {
        schemeDetails =>
          fsConnector.getSchemeFS(schemeDetails.pstr).flatMap {
            schemeFs =>
              val upcomingPaymentsAndCharges: Seq[SchemeFS] =
                paymentsAndChargesService
                  .getUpcomingCharges(schemeFs)

              if (upcomingPaymentsAndCharges.nonEmpty) {
                val paymentsAndChargesTables: Seq[PaymentsAndChargesTable] =
                  paymentsAndChargesService
                    .getPaymentsAndCharges(srn, upcomingPaymentsAndCharges, startDate.getYear, ChargeDetailsFilter.Upcoming)

                val tableWithoutPaymentStatusColumn: Seq[PaymentsAndChargesTable] =
                  paymentsAndChargesTables map {
                    table =>
                      PaymentsAndChargesTable(
                        caption = table.caption,
                        table = Table(
                          caption = table.table.caption,
                          captionClasses = table.table.captionClasses,
                          firstCellIsHeader = table.table.firstCellIsHeader,
                          head = table.table.head.take(table.table.head.size - 1),
                          rows = table.table.rows.map(p => p.take(p.size - 1)),
                          classes = table.table.classes,
                          attributes = table.table.attributes
                        )
                      )
                  }

                val heading =
                  if (upcomingPaymentsAndCharges.map(_.periodStartDate).distinct.size == 1)
                    msg"paymentsAndChargesUpcoming.h1.singlePeriod".withArgs(
                      upcomingPaymentsAndCharges.map(_.periodStartDate)
                        .distinct
                        .head
                        .format(DateTimeFormatter.ofPattern("d MMMM")),
                      upcomingPaymentsAndCharges.map(_.periodEndDate)
                        .distinct
                        .head
                        .format(DateTimeFormatter.ofPattern("d MMMM yyyy"))
                    )
                  else
                    msg"paymentsAndChargesUpcoming.h1.multiplePeriod"

                val json = Json.obj(
                  "heading" -> heading,
                  "upcomingPaymentsAndCharges" -> tableWithoutPaymentStatusColumn,
                  "schemeName" -> schemeDetails.schemeName,
                  "returnUrl" -> config.managePensionsSchemeSummaryUrl.format(srn)
                )
                renderer.render(template = "paymentsAndCharges/paymentsAndChargesUpcoming.njk", json).map(Ok(_))

              } else {
                Logger.warn(
                  s"No Upcoming Payments and Charges returned for the selected year ${startDate.getYear}"
                )
                Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
              }
          }
      }
  }
}
