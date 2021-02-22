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
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc._
import renderer.Renderer
import services.paymentsAndCharges.PaymentsAndChargesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Text}
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
                                                      paymentsAndChargesService: PaymentsAndChargesService,
                                                      renderer: Renderer
                                                    )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val logger = Logger(classOf[PaymentsAndChargesUpcomingController])

  def onPageLoad(srn: String, startDate: LocalDate): Action[AnyContent] = identify.async {
    implicit request =>
      paymentsAndChargesService.getPaymentsFromCache(request.idOrException, srn).flatMap { paymentsCache =>
        val schemeFS = paymentsCache.schemeFS.filter(_.periodStartDate == startDate)
        val upcomingPaymentsAndCharges: Seq[SchemeFS] = paymentsAndChargesService.extractUpcomingCharges(schemeFS)

        if (upcomingPaymentsAndCharges.nonEmpty) {


          val json = Json.obj(
            "heading" -> heading(startDate),
            "paymentAndChargesTable" -> tableWithoutPaymentStatusColumn(upcomingPaymentsAndCharges, srn),
            "schemeName" -> paymentsCache.schemeDetails.schemeName,
            "returnUrl" -> config.managePensionsSchemeSummaryUrl.format(srn)
          )
          renderer.render(template = "financialStatement/paymentsAndCharges/paymentsAndChargesUpcoming.njk", json).map(Ok(_))

        } else {
          logger.warn(
            s"No Upcoming Payments and Charges returned for the selected year ${startDate.getYear}"
          )
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
        }
    }
  }

  def tableWithoutPaymentStatusColumn(upcomingPaymentsAndCharges: Seq[SchemeFS], srn: String)
                                     (implicit messages: Messages): Table = {

    val table: Table = paymentsAndChargesService.getPaymentsAndCharges(srn, upcomingPaymentsAndCharges, ChargeDetailsFilter.Upcoming)

        Table(table.caption, table.captionClasses, table.firstCellIsHeader,
          table.head.take(table.head.size - 1),
          table.rows.map(p => p.take(p.size - 1)), table.classes, table.attributes
        )
  }

  val heading: LocalDate => Text.Message = startDate => msg"paymentsAndChargesUpcoming.h1.singlePeriod".withArgs(
        startDate.format(DateTimeFormatter.ofPattern("d MMMM")),
        Quarters.getQuarter(startDate).endDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy")))

}
