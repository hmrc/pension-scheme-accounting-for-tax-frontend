/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time.LocalDate

import config.FrontendAppConfig
import connectors.FinancialStatementConnector
import controllers.actions._
import dateOrdering._
import javax.inject.Inject
import models.financialStatement.SchemeFS
import models.viewModels.paymentsAndCharges.PaymentsAndChargesTable
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc._
import renderer.Renderer
import services.SchemeService
import services.paymentsAndCharges.PaymentsAndChargesService
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.{ExecutionContext, Future}

class PaymentsAndChargesController @Inject()(override val messagesApi: MessagesApi,
                                             identify: IdentifierAction,
                                             val controllerComponents: MessagesControllerComponents,
                                             config: FrontendAppConfig,
                                             schemeService: SchemeService,
                                             financialStatementConnector: FinancialStatementConnector,
                                             paymentsAndChargesService: PaymentsAndChargesService,
                                             renderer: Renderer)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def onPageLoad(srn: String, year: Int): Action[AnyContent] =
    identify.async { implicit request =>
      schemeService.retrieveSchemeDetails(request.psaId.id, srn).flatMap { schemeDetails =>
        financialStatementConnector.getSchemeFS(schemeDetails.pstr).flatMap { schemeFs =>
          val schemePaymentsAndChargesForSelectedYear = schemeFs.filter(_.periodStartDate.getYear == year)

          if (schemePaymentsAndChargesForSelectedYear.nonEmpty) {
            val schemePaymentsAndChargesGroupedWithPeriodStartDate: Seq[(LocalDate, Seq[SchemeFS])] =
              schemePaymentsAndChargesForSelectedYear.groupBy(_.periodStartDate).toSeq.sortWith(_._1 < _._1)

            val tableOfPaymentsAndCharges: Seq[PaymentsAndChargesTable] =
              paymentsAndChargesService.getPaymentsAndChargesSeqOfTables(schemePaymentsAndChargesGroupedWithPeriodStartDate, srn)

            val json = Json.obj(
              fields = "seqPaymentsAndChargesTable" -> tableOfPaymentsAndCharges,
              "schemeName" -> schemeDetails.schemeName,
              "returnUrl" -> config.managePensionsSchemeSummaryUrl.format(srn)
            )

            renderer.render(template = "paymentsAndCharges/paymentsAndCharges.njk", json).map(Ok(_))
          } else {
            Logger.warn(s"No Scheme Payments and Charges returned for the selected year $year")
            Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
          }
        }
      }
    }
}
