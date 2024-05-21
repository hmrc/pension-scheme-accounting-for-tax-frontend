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

package controllers

import config.FrontendAppConfig
import controllers.AFTOverviewController._
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, IdentifierAction}
import models.AFTQuarter.{formatForDisplayOneYear, monthDayStringFormat}
import models.financialStatement.PaymentOrChargeType.AccountingForTaxCharges
import models.{AFTQuarter, DisplayQuarter, SchemeDetails}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsObject, Json, OWrites}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.{QuartersService, SchemeService}
import uk.gov.hmrc.nunjucks.NunjucksSupport
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AFTOverviewController @Inject()(
                                       identify: IdentifierAction,
                                       renderer: Renderer,
                                       allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                       config: FrontendAppConfig,
                                       schemeService: SchemeService,
                                       quartersService: QuartersService,
                                       override val messagesApi: MessagesApi,
                                       val controllerComponents: MessagesControllerComponents
                                     )(implicit ec: ExecutionContext)
  extends FrontendBaseController with I18nSupport with NunjucksSupport {

//scalastyle:off
  def onPageLoad(srn: String): Action[AnyContent] = (identify andThen allowAccess(Some(srn))).async {
    implicit request =>

      (for {
        schemeDetails <- schemeService.retrieveSchemeDetails(
          psaId = request.idOrException,
          schemeIdType = schemeIdType,
          srn = srn
        )

        quartersInProgress <- quartersService.getInProgressQuarters(
          srn = srn,
          pstr = schemeDetails.pstr
        )

        allPastYears <- quartersService.getPastYears(
          pstr = schemeDetails.pstr
        )

        pastYearsAndQuarters <- Future.traverse(displayYears(allPastYears))(
          year => quartersService.getPastQuarters(
              pstr = schemeDetails.pstr,
              year = year
            ).flatMap(quarters => Future.successful(year, quarters))
        )

      } yield OverviewInfo(schemeDetails, quartersInProgress, pastYearsAndQuarters)
        ).flatMap { overviewInfo =>

        // TODO: remove hardcoded value
        val outstandingAmount: BigDecimal = 123.45

        val json: JsObject = Json.obj(
          viewModel -> OverviewViewModel(
            returnUrl = config.schemeDashboardUrl(request).format(srn),
            newAftUrl = controllers.routes.YearsController.onPageLoad(srn).url,
            paymentsAndChargesUrl = controllers.financialOverview.scheme.routes.SelectYearController.onPageLoad(srn, AccountingForTaxCharges).url,
            schemeName = overviewInfo.schemeDetails.schemeName,
            outstandingAmount = outstandingAmountStr(outstandingAmount),
            quartersInProgress = overviewInfo.quartersInProgress.map(qIP =>
              (formatForDisplayOneYear(qIP.quarter), linkForQuarter(srn, qIP.quarter))
            ),
            pastYearsAndQuarters = overviewInfo.pastYearsAndQuarters.map(pYAQ =>
              (pYAQ._1,
                pYAQ._2.map(q =>
                  (monthDayStringFormat(q.quarter), linkForQuarter(srn, q.quarter))
                )
              )
            )
          )
        )
        renderer.render(template, json).map(Ok(_))
      }
  }
}

object AFTOverviewController {

  private val schemeIdType = "srn"
  private val viewModel = "viewModel"
  private val template = "aftOverview.njk"
  private val maxPastYearsToDisplay = 3

  // TODO - remove "hardcoded" from string
  private val outstandingAmountStr: BigDecimal => String = amount => s"£$amount - hardcoded"

  private val linkForQuarter: (String, AFTQuarter) => String = (srn, aftQuarter) =>
    controllers.financialOverview.scheme.routes.AllPaymentsAndChargesController
      .onPageLoad(srn, aftQuarter.startDate.toString, AccountingForTaxCharges).url

  private val displayYears: Seq[Int] => Seq[Int] = seq => seq.sorted.reverse.take(maxPastYearsToDisplay)
  private case class OverviewInfo(
                           schemeDetails: SchemeDetails,
                           quartersInProgress: Seq[DisplayQuarter],
                           pastYearsAndQuarters: Seq[(Int, Seq[DisplayQuarter])]
                         )
  case class OverviewViewModel(
                                returnUrl: String,
                                newAftUrl: String,
                                paymentsAndChargesUrl: String,
                                schemeName: String,
                                outstandingAmount: String,
                                quartersInProgress: Seq[(String, String)] = Seq.empty,
                                pastYearsAndQuarters: Seq[(Int, Seq[(String, String)])] = Seq.empty
                              )

  private object OverviewViewModel {
    implicit lazy val writes: OWrites[OverviewViewModel] = Json.writes[OverviewViewModel]
  }
}
