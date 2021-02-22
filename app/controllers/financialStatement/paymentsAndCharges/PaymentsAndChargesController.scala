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
import controllers.actions._
import models.financialStatement.SchemeFS
import models.{ChargeDetailsFilter, Quarters}
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc._
import renderer.Renderer
import services.paymentsAndCharges.PaymentsAndChargesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}
import viewmodels.Table

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PaymentsAndChargesController @Inject()(
                                              override val messagesApi: MessagesApi,
                                              identify: IdentifierAction,
                                              val controllerComponents: MessagesControllerComponents,
                                              config: FrontendAppConfig,
                                              paymentsAndChargesService: PaymentsAndChargesService,
                                              renderer: Renderer
                                            )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val logger = Logger(classOf[PaymentsAndChargesController])

  def onPageLoad(srn: String, startDate: LocalDate): Action[AnyContent] = identify.async {
    implicit request =>
      paymentsAndChargesService.getPaymentsFromCache(request.idOrException, srn).flatMap { paymentsCache =>
              val filteredPayments: Seq[SchemeFS] = paymentsCache.schemeFS.filter(_.periodStartDate == startDate)

              if (filteredPayments.nonEmpty) {

                val tableOfPaymentsAndCharges: Table =
                  paymentsAndChargesService.getPaymentsAndCharges(srn, filteredPayments, ChargeDetailsFilter.All)

                val json = Json.obj(
                  fields =
                    "startDate" -> startDate.format(dateFormatterStartDate),
                    "endDate" -> Quarters.getQuarter(startDate).endDate.format(dateFormatterDMY),
                    "paymentAndChargesTable" -> tableOfPaymentsAndCharges,
                    "schemeName" -> paymentsCache.schemeDetails.schemeName,
                    "returnUrl" -> config.schemeDashboardUrl(request).format(srn)
                )
                renderer.render(template = "financialStatement/paymentsAndCharges/paymentsAndCharges.njk", json).map(Ok(_))

              } else {
                logger.warn(s"No Scheme Payments and Charges returned for the selected startDate $startDate")
                Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
              }

      }
  }
}
