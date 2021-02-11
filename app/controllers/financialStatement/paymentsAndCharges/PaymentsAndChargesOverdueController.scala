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

package controllers.financialStatement.paymentsAndCharges

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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import javax.inject.Inject

import scala.concurrent.{Future, ExecutionContext}

class PaymentsAndChargesOverdueController @Inject()(
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

  private val logger = Logger(classOf[PaymentsAndChargesOverdueController])

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
              val overduePaymentsAndCharges: Seq[SchemeFS] =
                paymentsAndChargesService
                  .getOverdueCharges(schemeFs)

              if (overduePaymentsAndCharges.nonEmpty) {
                val paymentsAndChargesTables: Seq[PaymentsAndChargesTable] =
                  paymentsAndChargesService
                    .getPaymentsAndCharges(srn, overduePaymentsAndCharges, startDate.getYear, ChargeDetailsFilter.Overdue)

                val heading =
                  if (overduePaymentsAndCharges.map(_.periodStartDate).distinct.size == 1) {
                    msg"paymentsAndChargesOverdue.h1.singlePeriod".withArgs(
                      overduePaymentsAndCharges.map(_.periodStartDate).distinct.head.format(DateTimeFormatter.ofPattern("d MMMM")),
                      overduePaymentsAndCharges.map(_.periodEndDate).distinct.head.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))
                    )
                  } else {
                    msg"paymentsAndChargesOverdue.h1.multiplePeriod"
                  }

                val json = Json.obj(
                  "heading" -> heading,
                  "overduePaymentsAndCharges" -> paymentsAndChargesTables,
                  "schemeName" -> schemeDetails.schemeName,
                  "returnUrl" -> config.managePensionsSchemeSummaryUrl.format(srn)
                )
                renderer.render(template = "financialStatement/paymentsAndCharges/paymentsAndChargesOverdue.njk", json).map(Ok(_))

              } else {
                logger.warn(
                  s"No Overdue Payments and Charges returned for the selected year ${startDate.getYear}"
                )
                Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
              }
          }
      }
  }
}
